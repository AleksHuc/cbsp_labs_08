package cbsp.lab08;

import com.jsyn.data.FloatSample;
import com.jsyn.unitgen.FixedRateStereoReader;
import com.jsyn.util.WaveRecorder;

final class JsynRoundTrip {
    private JsynRoundTrip() { }

    static void queueSample(FloatSample sample, FixedRateStereoReader reader) {
        // TODO (Task 1): queue the FloatSample in the stereo reader.
        throw new UnsupportedOperationException("TODO (Task 1): queue the stereo sample");
    }

    static void connectStereo(FixedRateStereoReader reader, WaveRecorder recorder) {
        // TODO (Task 1): connect each channel to the matching recorder input.
        throw new UnsupportedOperationException("TODO (Task 1): connect the stereo reader to the recorder");
    }

    static void copyChannels(FloatSample recorded, float[] left, float[] right) {
        // TODO (Task 1): split the interleaved recorded samples into two channels.
        throw new UnsupportedOperationException("TODO (Task 1): copy the recorded left and right samples");
    }
}
