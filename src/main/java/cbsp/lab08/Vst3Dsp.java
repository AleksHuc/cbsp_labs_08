package cbsp.lab08;

/** One instance per plug-in processor. JNI reuses the two channel arrays. */
final class Vst3Dsp {
    private final double sampleRate;
    private double cutoff;
    private double b0;
    private double a1;
    private double a2;
    private double state1;
    private double state2;

    Vst3Dsp(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    void process(float[] left, float[] right, int from, int to, double cutoffHz,
            double gain, boolean bypass, boolean reset) {
        // TODO (Task 4): apply channel subtraction and the stateful low-pass blend.
        throw new UnsupportedOperationException("TODO (Task 4): process the stereo block");
    }
}
