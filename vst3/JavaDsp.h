#pragma once

#include <jni.h>

namespace cbsp {

class JavaDsp {
public:
    JavaDsp() = default;
    JavaDsp(const JavaDsp&) = delete;
    JavaDsp& operator=(const JavaDsp&) = delete;
    ~JavaDsp();

    bool prepare(double sampleRate, int maxFrames);
    void prepareThread() const;
    bool process(const float* left, const float* right, float* outLeft, float* outRight,
                 int frames, double cutoff, double gain, bool bypass, bool reset) const;

private:
    void release();

    JavaVM* vm_ = nullptr;
    jobject dsp_ = nullptr;
    jfloatArray left_ = nullptr;
    jfloatArray right_ = nullptr;
    jmethodID process_ = nullptr;
    int maxFrames_ = 0;
};

} // namespace cbsp
