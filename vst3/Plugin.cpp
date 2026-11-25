#include "JavaDsp.h"

#include "base/source/fstreamer.h"
#include "pluginterfaces/vst/ivstparameterchanges.h"
#include "public.sdk/source/main/pluginfactory.h"
#include "public.sdk/source/vst/vstaudioeffect.h"
#include "public.sdk/source/vst/vsteditcontroller.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <limits>

namespace cbsp {
using namespace Steinberg;
using namespace Steinberg::Vst;

const FUID processorId(0x7672BE12, 0x8C024060, 0xACC70615, 0x1D6D52C9);
const FUID controllerId(0x4BB55A8F, 0xA51C47F0, 0xA06C82E6, 0x35677463);

enum ParameterId : ParamID { cutoff, gain, bypass, parameterCount };
constexpr double minCutoff = 20;
constexpr double maxCutoff = 2000;
constexpr double defaultCutoff = 180;
constexpr double defaultGain = 0.5;
constexpr double normalizedCutoff = (defaultCutoff - minCutoff) / (maxCutoff - minCutoff);
using Values = std::array<ParamValue, parameterCount>;

bool readValues(IBStream* state, Values& values) {
    if (!state) {
        return false;
    }
    IBStreamer stream(state, kLittleEndian);
    for (auto& value : values) {
        if (!stream.readDouble(value) || !std::isfinite(value) || value < 0 || value > 1) {
            return false;
        }
    }
    return true;
}

struct Points {
    IParamValueQueue* queue = nullptr;
    int32 index = 0;
    int32 offset = std::numeric_limits<int32>::max();
    ParamValue value = 0;

    void advance() {
        offset = std::numeric_limits<int32>::max();
        if (queue && index < queue->getPointCount()) {
            int32 sample = 0;
            if (queue->getPoint(index++, sample, value) == kResultTrue) {
                offset = sample;
            }
        }
    }
};

class Processor final : public AudioEffect {
public:
    Processor() { setControllerClass(controllerId); }
    static FUnknown* create(void*) { return static_cast<IAudioProcessor*>(new Processor); }

    tresult PLUGIN_API initialize(FUnknown* context) override {
        const auto result = AudioEffect::initialize(context);
        if (result != kResultOk) {
            return result;
        }
        addAudioInput(STR16("Stereo In"), SpeakerArr::kStereo);
        addAudioOutput(STR16("Stereo Out"), SpeakerArr::kStereo);
        return kResultOk;
    }

    tresult PLUGIN_API setBusArrangements(SpeakerArrangement* inputs, int32 numInputs,
                                         SpeakerArrangement* outputs, int32 numOutputs) override {
        if (numInputs != 1 || numOutputs != 1 ||
                inputs[0] != SpeakerArr::kStereo || outputs[0] != SpeakerArr::kStereo) {
            return kResultFalse;
        }
        return AudioEffect::setBusArrangements(inputs, numInputs, outputs, numOutputs);
    }

    tresult PLUGIN_API canProcessSampleSize(int32 size) override {
        return size == kSample32 ? kResultTrue : kResultFalse;
    }

    tresult PLUGIN_API setupProcessing(ProcessSetup& setup) override {
        const auto result = AudioEffect::setupProcessing(setup);
        if (result != kResultOk) {
            return result;
        }
        if (!java_.prepare(setup.sampleRate, setup.maxSamplesPerBlock)) {
            return kInternalError;
        }
        reset_ = true;
        return kResultOk;
    }

    tresult PLUGIN_API setProcessing(TBool state) override {
        if (state) {
            java_.prepareThread();
            reset_ = true;
        }
        return kResultOk;
    }

    tresult PLUGIN_API process(ProcessData& data) override {
        std::array<Points, parameterCount> points;
        if (auto* changes = data.inputParameterChanges) {
            for (int32 i = 0; i < changes->getParameterCount(); ++i) {
                if (auto* queue = changes->getParameterData(i)) {
                    const auto id = queue->getParameterId();
                    if (id < parameterCount) {
                        points[id].queue = queue;
                        points[id].advance();
                    }
                }
            }
        }
        if (data.numSamples == 0 || data.numInputs == 0 || data.numOutputs == 0) {
            for (auto& changes : points) {
                while (changes.offset != std::numeric_limits<int32>::max()) {
                    values_[&changes - points.data()] = std::clamp(changes.value, 0.0, 1.0);
                    changes.advance();
                }
            }
            return kResultOk;
        }
        if (data.symbolicSampleSize != kSample32 || data.inputs[0].numChannels != 2 ||
                data.outputs[0].numChannels != 2) {
            return kInvalidArgument;
        }
        auto** input = data.inputs[0].channelBuffers32;
        auto** output = data.outputs[0].channelBuffers32;
        if (!input || !output || !input[0] || !input[1] || !output[0] || !output[1]) {
            return kInvalidArgument;
        }

        for (int32 from = 0; from < data.numSamples;) {
            auto to = data.numSamples;
            for (std::size_t id = 0; id < points.size(); ++id) {
                auto& changes = points[id];
                while (changes.offset <= from) {
                    values_[id] = std::clamp(changes.value, 0.0, 1.0);
                    changes.advance();
                }
                to = std::min(to, changes.offset);
            }
            const auto hz = minCutoff + values_[cutoff].load() * (maxCutoff - minCutoff);
            if (!java_.process(input[0] + from, input[1] + from, output[0] + from,
                               output[1] + from, to - from, hz, values_[gain], values_[bypass] >= 0.5,
                               reset_)) {
                return kInternalError;
            }
            reset_ = false;
            from = to;
        }
        data.outputs[0].silenceFlags = 0;
        for (int channel = 0; channel < 2; ++channel) {
            if (std::all_of(output[channel], output[channel] + data.numSamples,
                            [](float sample) { return sample == 0; })) {
                data.outputs[0].silenceFlags |= 1ULL << channel;
            }
        }
        return kResultOk;
    }

    tresult PLUGIN_API setState(IBStream* state) override {
        Values values;
        if (!readValues(state, values)) {
            return kResultFalse;
        }
        for (std::size_t id = 0; id < values.size(); ++id) {
            values_[id] = values[id];
        }
        return kResultOk;
    }

    tresult PLUGIN_API getState(IBStream* state) override {
        if (!state) {
            return kInvalidArgument;
        }
        IBStreamer stream(state, kLittleEndian);
        for (const auto& value : values_) {
            if (!stream.writeDouble(value.load())) {
                return kResultFalse;
            }
        }
        return kResultOk;
    }

private:
    JavaDsp java_;
    std::array<std::atomic<ParamValue>, parameterCount> values_ {normalizedCutoff, defaultGain, 0.0};
    bool reset_ = true;
};

class Controller final : public EditController {
public:
    static FUnknown* create(void*) { return static_cast<IEditController*>(new Controller); }

    tresult PLUGIN_API initialize(FUnknown* context) override {
        const auto result = EditController::initialize(context);
        if (result != kResultOk) {
            return result;
        }
        auto* frequency = new RangeParameter(STR16("Cutoff"), cutoff, STR16("Hz"),
                                             minCutoff, maxCutoff, defaultCutoff);
        frequency->setPrecision(1);
        parameters.addParameter(frequency);
        auto* blend = new RangeParameter(STR16("Low blend"), gain, nullptr, 0, 1, defaultGain);
        blend->setPrecision(3);
        parameters.addParameter(blend);
        parameters.addParameter(STR16("Bypass"), nullptr, 1, 0,
                                ParameterInfo::kCanAutomate | ParameterInfo::kIsBypass, bypass);
        return kResultOk;
    }

    tresult PLUGIN_API setComponentState(IBStream* state) override {
        Values values;
        if (!readValues(state, values)) {
            return kResultFalse;
        }
        for (ParamID id = 0; id < values.size(); ++id) {
            setParamNormalized(id, values[id]);
        }
        return kResultOk;
    }
};

} // namespace cbsp

using namespace Steinberg;
using namespace Steinberg::Vst;

BEGIN_FACTORY_DEF("CBSP", "", "")

DEF_CLASS2(INLINE_UID_FROM_FUID(cbsp::processorId), PClassInfo::kManyInstances,
           kVstAudioEffectClass, "CBSP Vocal Reduction", Vst::kDistributable,
           PlugType::kFx, "1.0.0", kVstVersionString, cbsp::Processor::create)

DEF_CLASS2(INLINE_UID_FROM_FUID(cbsp::controllerId), PClassInfo::kManyInstances,
           kVstComponentControllerClass, "CBSP Vocal Reduction Controller", 0, "",
           "1.0.0", kVstVersionString, cbsp::Controller::create)

END_FACTORY
