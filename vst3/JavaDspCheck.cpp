#include "JavaDsp.h"

#include <array>
#include <cmath>
#include <iostream>
#include <thread>

int main() {
    cbsp::JavaDsp bridge;
    if (!bridge.prepare(48000, 8)) {
        std::cerr << "JNI setup failed\n";
        return 1;
    }

    bool passed = false;
    std::thread host([&] {
        const std::array<float, 8> left {0.2f, 0.25f, -0.3f, 0.5f, 0, -0.1f, 0.2f, 0.3f};
        const std::array<float, 8> right {0.2f, 0, 0.5f, 0.5f, 0, -0.3f, 0.2f, -0.5f};
        std::array<float, 8> outL {}, outR {}, splitL {}, splitR {};
        if (!bridge.process(left.data(), right.data(), outL.data(), outR.data(),
                            8, 180, 0, false, true)) {
            return;
        }
        for (std::size_t i = 0; i < outL.size(); ++i) {
            if (outL[i] != outR[i] || std::abs(outL[i] - (left[i] - right[i]) / 2) > 1e-7f) {
                return;
            }
        }
        if (!bridge.process(left.data(), right.data(), outL.data(), outR.data(), 8, 180, 0.5, false, true)
                || !bridge.process(left.data(), right.data(), splitL.data(), splitR.data(), 3, 180, 0.5, false, true)
                || !bridge.process(left.data() + 3, right.data() + 3, splitL.data() + 3, splitR.data() + 3,
                                   5, 180, 0.5, false, false)
                || outL != outR || outL != splitL || outR != splitR || outL[0] <= 0) {
            return;
        }
        passed = bridge.process(left.data(), right.data(), outL.data(), outR.data(), 8, 180, 0.5, true, true)
                && outL == left && outR == right;
    });
    host.join();
    if (!passed) {
        std::cerr << "JNI DSP check failed\n";
        return 1;
    }
    std::cout << "JNI stereo, unequal blocks, reset and bypass verified\n";
}
