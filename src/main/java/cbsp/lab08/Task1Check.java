package cbsp.lab08;

import com.jsyn.data.FloatSample;
import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.UnsupportedAudioFileException;

final class Task1Check {
    private static final double PCM_UNIT = 1.0 / 32768;

    private Task1Check() { }

    static void run(Path input, Path output)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        FloatSample original = JsynRender.renderRoundTrip(input, output);
        FloatSample recorded = JsynRender.loadStereoWav(output);
        int frames = recorded.getNumFrames();
        if (original.getFrameRate() != recorded.getFrameRate() || original.getNumFrames() != frames) {
            throw new IOException("Round-trip WAV has a different sample rate or frame count.");
        }
        float[] left = new float[frames];
        float[] right = new float[frames];
        JsynRoundTrip.copyChannels(recorded, left, right);

        double leftPeak = 0;
        double rightPeak = 0;
        for (int frame = 0; frame < frames; frame++) {
            leftPeak = Math.max(leftPeak, Math.abs(left[frame] - original.readDouble(2 * frame)));
            rightPeak = Math.max(rightPeak, Math.abs(right[frame] - original.readDouble(2 * frame + 1)));
        }
        if (!(leftPeak <= PCM_UNIT && rightPeak <= PCM_UNIT)) {
            throw new IOException("Round-trip samples differ by more than one 16-bit PCM unit.");
        }
        System.out.printf("%s → %s (%d Hz, %d stereo frames).%n",
                input.getFileName(), output, (int) recorded.getFrameRate(), frames);
        System.out.printf("Maximum difference in 16-bit PCM units: L = %.0f, R = %.0f%n",
                leftPeak / PCM_UNIT, rightPeak / PCM_UNIT);
    }
}
