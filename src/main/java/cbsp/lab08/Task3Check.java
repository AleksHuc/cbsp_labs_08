package cbsp.lab08;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

final class Task3Check {
    private Task3Check() { }

    static void run(Path input, Path outputDirectory)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        BassBlend.Settings settings = BassBlend.DEFAULT;
        Path baseline = outputDirectory.resolve("task3-control-difference.wav");
        Path zero = outputDirectory.resolve("task3-control-zero.wav");
        Path blended = outputDirectory.resolve("task3-control-low.wav");
        JsynRender.render(input, baseline);
        JsynRender.render(input, zero, new BassBlend.Settings(settings.cutoffHz(), 0));
        if (Files.mismatch(baseline, zero) != -1) {
            throw new IOException("Pri g = 0 mora biti izhod tretje naloge enak izhodu druge naloge.");
        }
        JsynRender.render(input, blended, settings);
        checkBlend(input, blended, settings);
    }

    private static void checkBlend(Path input, Path output, BassBlend.Settings settings)
            throws IOException, UnsupportedAudioFileException {
        // The bundled control uses signed little-endian stereo PCM, 16 bits.
        try (var source = AudioSystem.getAudioInputStream(input.toFile());
             var result = AudioSystem.getAudioInputStream(output.toFile())) {
            if (!source.getFormat().matches(result.getFormat())
                    || source.getFrameLength() != result.getFrameLength()) {
                throw new IOException("Izhodni format ali število vzorcev se ne ujema z vhodom.");
            }
            ByteBuffer original = ByteBuffer.wrap(source.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer rendered = ByteBuffer.wrap(result.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            if (original.remaining() != source.getFrameLength() * 4L
                    || rendered.remaining() != original.remaining()) {
                throw new IOException("Nepopolni podatki PCM v kontrolnem posnetku.");
            }

            // Independent Butterworth reference using the bilinear transform.
            double k = Math.tan(Math.PI * settings.cutoffHz() / source.getFormat().getSampleRate());
            double denominator = 1 + Math.sqrt(2.0) * k + k * k;
            double b0 = k * k / denominator;
            double a1 = 2 * (k * k - 1) / denominator;
            double a2 = (1 - Math.sqrt(2.0) * k + k * k) / denominator;
            double state1 = 0;
            double state2 = 0;
            int peak = 0;
            for (int frame = 0; original.hasRemaining(); frame++) {
                double left = original.getShort() / 32768.0;
                double right = original.getShort() / 32768.0;
                double average = (left + right) / 2;
                double filtered = b0 * average + state1;
                state1 = 2 * b0 * average - a1 * filtered + state2;
                state2 = b0 * average - a2 * filtered;
                double expected = ((left - right) / 2 + settings.gain() * filtered) * Short.MAX_VALUE;
                int actual = rendered.getShort();
                if (actual != rendered.getShort() || Math.abs(actual - expected) > 1) {
                    throw new IOException("Nepravilen izhod nizkofrekvenčne veje pri vzorcu " + frame + ".");
                }
                peak = Math.max(peak, Math.abs(actual));
            }
            if (peak >= Short.MAX_VALUE) {
                throw new IOException("Kontrolni izvoz doseže mejo zapisa PCM.");
            }
            System.out.printf("%s (%d vzorcev na kanal, vrh %.1f dBFS, oba preizkusa uspešna)%n",
                    output, result.getFrameLength(), 20 * Math.log10(peak / (double) Short.MAX_VALUE));
        }
    }
}
