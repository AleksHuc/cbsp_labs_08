#include "JavaDsp.h"
#include "Vst3DspClass.h"

#include <mutex>

namespace cbsp {
namespace {

constexpr char className[] = "cbsp/lab08/Vst3Dsp";
constexpr auto jniVersion = JNI_VERSION_1_8;

struct Attachment {
    JavaVM* vm = nullptr;

    ~Attachment() {
        if (vm) {
            vm->DetachCurrentThread();
        }
    }
};

JNIEnv* environment(JavaVM* vm) {
    void* env = nullptr;
    if (vm->GetEnv(&env, jniVersion) == JNI_OK) {
        return static_cast<JNIEnv*>(env);
    }
    static thread_local Attachment attachment;
    if (vm->AttachCurrentThreadAsDaemon(&env, nullptr) != JNI_OK) {
        return nullptr;
    }
    attachment.vm = vm;
    return static_cast<JNIEnv*>(env);
}

bool succeeded(JNIEnv* env) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    return true;
}

struct Runtime {
    JavaVM* vm = nullptr;
    jclass type = nullptr;
    jmethodID constructor = nullptr;
    jmethodID process = nullptr;
};

Runtime runtime() {
    static std::mutex lock;
    static Runtime shared;
    const std::lock_guard guard(lock);
    if (!shared.vm) {
        jsize count = 0;
        if (JNI_GetCreatedJavaVMs(&shared.vm, 1, &count) != JNI_OK) {
            return {};
        }
        if (count == 0) {
            char reducedSignals[] = "-Xrs";
            JavaVMOption options[] {{reducedSignals, nullptr}};
            JavaVMInitArgs args {jniVersion, 1, options, JNI_FALSE};
            void* env = nullptr;
            JavaVM* vm = nullptr;
            if (JNI_CreateJavaVM(&vm, &env, &args) != JNI_OK) {
                return {};
            }
            shared.vm = vm;
            vm->DetachCurrentThread();
        }
    }
    if (!shared.type) {
        JNIEnv* env = environment(shared.vm);
        if (!env) {
            return {};
        }
        jclass type = env->FindClass(className);
        if (!succeeded(env)) {
            type = env->DefineClass(className, nullptr,
                reinterpret_cast<const jbyte*>(vst3DspClass), sizeof(vst3DspClass));
        }
        if (!succeeded(env) || !type) {
            return {};
        }
        jmethodID constructor = env->GetMethodID(type, "<init>", "(D)V");
        if (!succeeded(env) || !constructor) {
            env->DeleteLocalRef(type);
            return {};
        }
        jmethodID process = env->GetMethodID(type, "process", "([F[FIIDDZZ)V");
        if (!succeeded(env) || !process) {
            env->DeleteLocalRef(type);
            return {};
        }
        shared.type = static_cast<jclass>(env->NewGlobalRef(type));
        env->DeleteLocalRef(type);
        if (!succeeded(env) || !shared.type) {
            return {};
        }
        shared.constructor = constructor;
        shared.process = process;
    }
    return shared;
}

template<class T>
T globalReference(JNIEnv* env, T local) {
    if (!succeeded(env) || !local) {
        return nullptr;
    }
    T global = static_cast<T>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    return succeeded(env) ? global : nullptr;
}

} // namespace

JavaDsp::~JavaDsp() {
    release();
}

void JavaDsp::release() {
    if (vm_) {
        if (JNIEnv* env = environment(vm_)) {
            env->DeleteGlobalRef(dsp_);
            env->DeleteGlobalRef(left_);
            env->DeleteGlobalRef(right_);
        }
    }
    dsp_ = nullptr;
    left_ = nullptr;
    right_ = nullptr;
    maxFrames_ = 0;
}

bool JavaDsp::prepare(double sampleRate, int maxFrames) {
    release();
    const auto java = runtime();
    if (!java.type || maxFrames <= 0) {
        return false;
    }
    vm_ = java.vm;
    JNIEnv* env = environment(vm_);
    if (!env) {
        return false;
    }
    dsp_ = globalReference(env, env->NewObject(java.type, java.constructor, sampleRate));
    left_ = globalReference(env, env->NewFloatArray(maxFrames));
    right_ = globalReference(env, env->NewFloatArray(maxFrames));
    if (!dsp_ || !left_ || !right_) {
        release();
        return false;
    }
    process_ = java.process;
    maxFrames_ = maxFrames;

    // Resolve Java classes and initialize the DSP path before the first host block.
    float zero = 0;
    return process(&zero, &zero, &zero, &zero, 1, 180.0, 0.5, false, true);
}

void JavaDsp::prepareThread() const {
    if (vm_) {
        environment(vm_);
    }
}

bool JavaDsp::process(const float* left, const float* right, float* outLeft, float* outRight,
                      int frames, double cutoff, double gain, bool bypass, bool reset) const {
    if (!dsp_ || frames <= 0 || frames > maxFrames_) {
        return false;
    }
    JNIEnv* env = environment(vm_);
    if (!env) {
        return false;
    }
    env->SetFloatArrayRegion(left_, 0, frames, left);
    env->SetFloatArrayRegion(right_, 0, frames, right);
    env->CallVoidMethod(dsp_, process_, left_, right_, 0, frames, cutoff, gain,
                        static_cast<jboolean>(bypass), static_cast<jboolean>(reset));
    if (!succeeded(env)) {
        return false;
    }
    env->GetFloatArrayRegion(left_, 0, frames, outLeft);
    env->GetFloatArrayRegion(right_, 0, frames, outRight);
    return succeeded(env);
}

} // namespace cbsp
