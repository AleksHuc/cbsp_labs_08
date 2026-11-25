# Lab 08: Vocal reduction with JSyn and VST3

Trace stereo samples through JSyn, reduce centered vocals by subtracting
the channels, and restore some of the bass from the channel average.
Compare your JSyn exports with the output of a VST3 effect in Audacity.

This is a Java starter for **four required tasks**. `TODO (Task N)` comments
mark the methods you must complete. Each unfinished method throws
`UnsupportedOperationException("TODO (Task N): ...")` with the task number and
the missing step. The WAV files, JSyn renderer, checks, C++ plug-in and
JNI bridge are supplied.

## Setup and commands

Use **JDK 21 or newer** and **Maven 3.9 or newer**. Open the directory
containing `pom.xml` as a Maven project. The first build needs access to
JitPack to download JSyn. The Java checks and exports run without an
audio device. [JSyn](https://github.com/philburk/jsyn)

Run from the project root.

```bash
mvn clean compile
mvn -q compile exec:java -Dexec.args=--help
```

The starter compiles and `--help` works immediately. Checks and JSyn
exports fail at the first unfinished TODO they reach. The commands in
each task compile your latest changes before running. In IntelliJ, run
`cbsp.lab08.Main` with the appropriate program arguments and the project
root as the working directory.

- `--check1` through `--check4` run the supplied checks. For Task 1,
  `--check1` also produces the listening export.
- `--task2 [input.wav [output.wav]]` exports the channel difference.
- `--task3 [cutoffHz gain [input.wav [output.wav]]]` exports the channel
  difference with the low-frequency branch. The default settings are
  180 Hz and a gain of 0.5.
- `--task4` compares the two Audacity exports with their JSyn references.

Tasks 2 and 3 use `media/skladba.wav` by default. A custom input must be
a stereo WAV with 16-, 24- or 32-bit integer PCM or 32-bit floating-point
PCM. JSyn exports 16-bit stereo WAV at the input sample rate. Default
exports go to **`output/`**. Running a command again replaces any export
it writes at the same path.

## Implementation order and TODO locations

Source files are in `src/main/java/cbsp/lab08/`. Complete the marked
methods in order, keeping their signatures and the supplied checks. Run
the check for each task before moving on.

| Task | Implement | Earlier tasks needed |
| ---- | --------- | -------------------- |
| 1 | `JsynRoundTrip.queueSample`, `JsynRoundTrip.connectStereo`, `JsynRoundTrip.copyChannels` | None |
| 2 | `ChannelDifference.build` | 1 |
| 3 | `BassBlend.build` | 1 and 2 |
| 4 | `Vst3Dsp.process` | 1–3 for the JSyn comparison |

## Task 1 · Read and record stereo samples in JSyn

**Goal.** Pass a stereo signal through a JSyn reader and recorder, then retrieve and verify the recorded left and right samples.

**Your work in `JsynRoundTrip.java`.** Complete the three marked methods.

1. In `queueSample`, queue the supplied `FloatSample` in the `FixedRateStereoReader` using its `dataQueue` port.
2. In `connectStereo`, connect reader output 0 to recorder input 0 and reader output 1 to recorder input 1. The left and right signals pass directly through the graph.
3. In `copyChannels`, split the recorded `FloatSample` into the supplied `left` and `right` arrays. Each stereo frame contains a left sample followed by a right sample. For frame `n`, use `readDouble` with sample indices `2 * n` and `2 * n + 1`. Store the two values at index `n` in their respective arrays.

**Supplied setup.** The renderer uses `SampleLoader` to load the input WAV and runs the synthesizer at the input sample rate. `WaveRecorder` writes a 16-bit stereo WAV. Once the recorder has closed the file, the check loads it back into a `FloatSample` before calling `copyChannels`. Tasks 2 and 3 use the same `queueSample` method.

### Run and inspect

```bash
mvn -q compile exec:java -Dexec.args=--check1
```

Compare `media/kontrolna-mesanica.wav` with `output/task1-round-trip.wav`.
The recordings should sound the same, with the guitar still on the left.
The check verifies the sample rate and frame count, then reports the
maximum absolute difference for each channel. It allows one 16-bit PCM
unit for rounding.

## Task 2 · Subtract the channels in JSyn

**Goal.** For each pair of time-aligned samples, calculate **S[n] = (L[n] − R[n]) / 2**. Signal components that are identical in both channels cancel out.

**Your work in `ChannelDifference.java`.** Complete the `build` method. The `stereo` parameter carries the left channel on output 0 and the right channel on output 1. The supplied `difference` unit sums the processed channels.

1. Add two `Multiply` units from `com.jsyn.unitgen` to the synthesizer.
2. Connect each channel to `inputA` on its own `Multiply` unit. Set `inputB` to +0.5 for the left channel and −0.5 for the right channel.
3. Connect the multiplier outputs to the inputs of `difference` and return `difference.output`. The supplied recorder writes the sum to both output channels (_dual mono_).

**Supplied setup.** `JsynRender.java` uses the reader setup from Task 1. The reader consumes one input sample per output sample, and the synthesizer runs at the input file’s sample rate. `WaveRecorder` writes the same number of samples per channel as the input contains.

### Run and listen

```bash
mvn -q compile exec:java -Dexec.args=--check2
mvn -q compile exec:java -Dexec.args=--task2
```

1. Before running the task, predict the result for identical channels, for a guitar heard only in the left channel, and for vocals with amplitude **v** on the left and **0.8 v** on the right.
2. Run `--check2` and listen to both test exports.
3. Run `--task2` and compare `media/skladba.wav` with `output/task2-jsyn.wav`. The supplied excerpt contains a synthesized speaking voice and instrumental accompaniment. Listen for what remains of the accompaniment, the voice, and its delayed echo.

| Input in `media/`        | Export in `output/`            | Expected result                                                                                                |
| ------------------------ | ------------------------------ | -------------------------------------------------------------------------------------------------------------- |
| `enaka-kanala.wav`       | `task2-enaka-kanala.wav`       | Silence in both channels.                                                                                      |
| `kontrolna-mesanica.wav` | `task2-kontrolna-mesanica.wav` | The voice and bass cancel out. The guitar remains in both channels at half its input amplitude.                |
| `skladba.wav`            | `task2-jsyn.wav`               | Signal components shared by both channels cancel out. Some of the voice may remain because of the stereo echo. |

For both test recordings, `--check2` verifies the sample rate, sample count, equality of the output channels, and the difference from the expected value at each sample. For nonzero results, it allows a rounding difference of one 16-bit PCM unit. For the unequal vocal amplitudes in step 1, calculate how much remains and verify that the result is **0.1 v**.

## Task 3 · Restore low frequencies in JSyn

**Goal.** Add the low-frequency part of the channel average to the channel difference **S** to restore some of the bass that canceled out during subtraction.

**Your work in `BassBlend.java`.** Complete the `build` method. The `output` unit already receives the channel difference from Task 2 on `inputA`.

1. Use two `Multiply` units with a factor of +0.5 and one `Add` unit to calculate **M[n] = (L[n] + R[n]) / 2**.
2. Connect the average to the input of `FilterLowPass`. Set its `frequency` input to the cutoff from `settings.cutoffHz()` and its `Q` input to `BUTTERWORTH_Q` (1/√2).
3. Multiply the filter output by `settings.gain()`, connect it to `output.inputB` and return `output.output`. The recorder writes **Y[n] = S[n] + g · LPF(M)[n]** to both channels.

### Run and listen

```bash
mvn -q compile exec:java -Dexec.args=--check3
mvn -q compile exec:java "-Dexec.args=--task3 180 0"
mvn -q compile exec:java -Dexec.args=--task3
```

1. Run `--task3 180 0` and compare `task3-jsyn-zero.wav` with the output of Task 2.
2. Run `--task3` and listen to `task3-jsyn-low.wav`. Listen for how much bass returns and whether the low-frequency parts of the voice also become more audible.
3. Try another cutoff frequency and gain. Use a decimal point in command arguments, as in `--task3 250 0.3 media/kontrolna-mesanica.wav output/moj-preizkus.wav`. The gain must be finite and nonnegative. The cutoff frequency must be between 0.00001 Hz and 0.499 times the sample rate. If the signal reaches full scale, reduce the gain.

`--check3` exports the test mix as `task3-control-difference.wav`, `task3-control-zero.wav`, and `task3-control-low.wav` in `output/`. The first two exports should match byte for byte. At the default gain, the bass should be audible again. The check compares both channels with a calculated Butterworth filter reference, allows a difference of one 16-bit PCM unit, and prints the peak level of the test export in dBFS.

## Task 4 · Run a VST3 effect in Audacity

**Goal.** Implement channel subtraction and low-frequency restoration as a stereo effect named **CBSP Vocal Reduction**. Compare the Audacity exports with both JSyn references.

### 1. Implement the Java DSP

**Your work in `Vst3Dsp.java`.** Complete `process`. Process only the samples from `from` inclusive to `to` exclusive. The C++ module in `vst3/` starts the JVM. In `setupProcessing`, it prepares the Java DSP and two channel arrays sized for the host’s maximum block size. The JNI bridge reuses those arrays on every call. If the host sends a block from a new thread, that thread attaches to the JVM on its first call. [VST3 IAudioProcessor](https://steinbergmedia.github.io/vst3_doc/vstinterfaces/classSteinberg_1_1Vst_1_1IAudioProcessor.html)

Use one second-order IIR section (a biquad) with **Q = 1/√2**, as in the JSyn filter from Task 3. The filter passes very low frequencies at unity gain and attenuates the signal by about 3 dB at the cutoff. [Audio EQ Cookbook](https://www.w3.org/TR/audio-eq-cookbook/)

**Compute the coefficients.** Let `fs` be the host sample rate and `fc` the cutoff, both in Hz. On the first call and whenever `cutoffHz` changes, compute the following values in `double`. Limit `fc` to `0.499 * fs` to match JSyn near the Nyquist frequency.

```text
fc = min(cutoffHz, 0.499 * fs)
k  = tan(π * fc / fs)
d  = 1 + √2 * k + k²

b0 = k² / d
b1 = 2 * b0
b2 = b0
a1 = 2 * (k² - 1) / d
a2 = (1 - √2 * k + k²) / d
```

The coefficients are already normalized so that `a0 = 1`. Store `b0`, `a1`, and `a2` in the DSP instance. You can derive `b1` and `b2` from `b0`.

**Prepare the filter state.** Keep `state1` and `state2` in `double` fields so each block starts with the state left by the preceding block. When `reset` is true, set both fields to zero before processing the block.

**Filter each sample.** Read the original values `L` and `R` from the supplied arrays. Calculate **S = (L − R) / 2** and **M = (L + R) / 2**. Use the transposed direct form II with `x = M` and update the state in the order shown.

```text
low    = b0 * x + state1
state1 = b1 * x - a1 * low + state2
state2 = b2 * x - a2 * low
```

The update for `state1` uses the previous `state2`. After updating both fields, write **Y = S + g · low** to both channels. When `bypass` is true, leave the input samples in the arrays and continue updating the filter state.

**Check the result.** At `fs = 48000` Hz and `fc = 180` Hz, the coefficients are approximately `b0 = 0.0001365107`, `a1 = -1.966681385`, and `a2 = 0.9672274282`. For a unit impulse followed by zeros, the first two filtered samples after a reset should be about `0.0001365107` and `0.0005414945`.

### 2. Verify the Java DSP and generate the JSyn references

```bash
mvn -q compile exec:java -Dexec.args=--check4
mvn -q compile exec:java -Dexec.args=--task2
mvn -q compile exec:java -Dexec.args=--task3
```

`--check4` processes each supplied recording in one block and in two blocks of different lengths. Both runs must produce identical output. The check also compares the Java DSP with JSyn at gains 0 and 0.5. The next two commands create the references for the Audacity exports.

### 3. Build and validate the VST3 plug-in

These instructions are for Linux. You need CMake 3.25 or later, a C++17 compiler, Git, JDK 21 or later, and Audacity with VST3 support. The JDK and Audacity must use the same CPU architecture. Set `JAVA_HOME` to the JDK root. The computer that loads the bundle must have access to the JVM library from that JDK. [Steinberg VST3 SDK](https://github.com/steinbergmedia/vst3sdk/tree/v3.8.0_build_66)

```bash
mkdir -p build
git clone --branch v3.8.0_build_66 --recurse-submodules \
  https://github.com/steinbergmedia/vst3sdk.git build/vst3sdk
cmake -S vst3 -B build/vst3 -DCMAKE_BUILD_TYPE=Release \
  -DVST3_SDK_DIR="$PWD/build/vst3sdk"
cmake --build build/vst3 --config Release --parallel \
  --target CbspVocalReduction cbsp_jni_check validator
ctest --test-dir build/vst3 -C Release --output-on-failure
build/vst3/bin/Release/validator build/vst3/VST3/Release/CbspVocalReduction.vst3
```

### 4. Install and set up the effect in Audacity

1. Copy the entire `build/vst3/VST3/Release/CbspVocalReduction.vst3` directory into `~/.vst3/`, or add `build/vst3/VST3/Release/` to the VST3 plug-in locations in Audacity. Rescan in the plug-in manager. After rebuilding, restart Audacity to load the new version. [Installing Plug-ins in Audacity](https://support.audacityteam.org/basics/customizing-audacity/installing-plugins)
2. In Audacity, choose **None** under **Quality → High-quality Conversion → Dither** for sample-by-sample comparisons. [Audacity Quality Preferences](https://manual.audacityteam.org/man/quality_preferences.html)
3. Import `media/skladba.wav` as a stereo track. Set the track gain to 0 dB and center the pan. On that track, choose **Effects → Add effect** and add **CBSP Vocal Reduction**. Open its controls for **Cutoff** (20–2000 Hz), **Low blend** (0–1), and **Bypass**. [Real-time Effects in Audacity](https://support.audacityteam.org/audio-editing/using-realtime-effects)

### 5. Listen, test and export from Audacity

1. Leave **Cutoff** at 180 Hz and set **Low blend** to 0. Listen to the excerpt and use **Bypass** or the switch next to the effect to compare it with the original.
2. Open `media/enaka-kanala.wav` and `media/kontrolna-mesanica.wav` in separate projects and add the effect to each. With **Bypass** off and **Low blend** at 0, identical channels should produce silence and the guitar in the test mix should remain at half its input amplitude. Increase **Low blend** in the test mix and listen for the bass returning.
3. Return to the excerpt. Enable the effect and turn off **Bypass**. For both exports, choose 16-bit PCM stereo WAV at 48 kHz and export the full excerpt from its first sample. At **Low blend** 0, save `output/task4-vst3.wav`. At **Low blend** 0.5, listen to the difference and save `output/task4-vst3-low.wav`.

### 6. Compare the Audacity exports with JSyn

```bash
mvn -q compile exec:java -Dexec.args=--task4
```

The command compares `task4-vst3.wav` with `task2-jsyn.wav` and `task4-vst3-low.wav` with `task3-jsyn-low.wav`. It verifies the sample rate, sample count, and equality of the output channels. It reports the maximum absolute difference from the reference for each channel and allows two 16-bit PCM units for rounding during export.

## Verification

Run `--check1` through `--check4` after completing the Java tasks.
`ctest` tests the JNI bridge with unequal blocks, a reset and bypass.
The VST3 validator tests the built bundle. Run `--task4` after exporting
both versions from Audacity to compare them with the JSyn references.

## Code map

| File | Responsibility |
| ---- | -------------- |
| `JsynRoundTrip.java` | Stereo reader, recorder connections and sample extraction exercise |
| `ChannelDifference.java`, `BassBlend.java` | JSyn subtraction and low-frequency branch exercises |
| `Vst3Dsp.java` | Block processing exercise for the plug-in |
| `Main.java`, `JsynRender.java` | Command dispatch, stereo WAV loading and offline recording |
| `Task1Check.java`–`Task4Check.java` | Sample and export checks |
| `vst3/Plugin.cpp`, `vst3/JavaDsp.cpp` | VST3 processor, controls and JNI bridge |
| `vst3/CMakeLists.txt`, `vst3/EmbedClass.cmake`, `vst3/JavaDspCheck.cpp` | Build, Java class embedding and JNI check |
| `media/` | Identical-channel recording, control mix and listening excerpt |
