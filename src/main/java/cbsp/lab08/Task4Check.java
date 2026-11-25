package cbsp.lab08;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

final class Task4Check {
    private static final int PCM_SCALE = 32768;
    private static final int TOLERANCE = 2;

    private Task4Check() { }

    static void run(Path media) throws IOException, UnsupportedAudioFileException, InterruptedException {
        Path reference = Files.createTempFile("cbsp-jsyn-", ".wav");
        try {
            for (String name : new String[] {"enaka-kanala.wav", "kontrolna-mesanica.wav", "skladba.wav"}) {
                Path path = media.resolve(name);
                Stereo input = read(path);
                JsynRender.render(path, reference);
                checkSamples(read(reference), process(input, 0), name + " g = 0");

                JsynRender.render(path, reference, BassBlend.DEFAULT);
                checkSamples(read(reference), process(input, BassBlend.DEFAULT.gain()), name + " g = 0.5");
            }
        } finally {
            Files.deleteIfExists(reference);
        }
    }

    static void compareExports(Path output) throws IOException, UnsupportedAudioFileException {
        compare(output, "task2-jsyn.wav", "task4-vst3.wav");
        compare(output, "task3-jsyn-low.wav", "task4-vst3-low.wav");
    }

    private static void compare(Path folder, String reference, String result)
            throws IOException, UnsupportedAudioFileException {
        checkSamples(read(folder.resolve(reference)), read(folder.resolve(result)), result);
    }

    private static Stereo process(Stereo input, double gain) throws IOException {
        Vst3Dsp dsp = new Vst3Dsp(input.rate);
        float[] wholeLeft = input.left.clone();
        float[] wholeRight = input.right.clone();
        double cutoff = BassBlend.DEFAULT.cutoffHz();
        dsp.process(wholeLeft, wholeRight, 0, wholeLeft.length, cutoff, gain, false, true);

        float[] left = input.left.clone();
        float[] right = input.right.clone();
        int middle = left.length / 3;
        dsp.process(left, right, 0, middle, cutoff, gain, false, true);
        dsp.process(left, right, middle, left.length, cutoff, gain, false, false);
        if (!Arrays.equals(left, wholeLeft) || !Arrays.equals(right, wholeRight)) {
            throw new IOException("Razdelitev na bloke je spremenila izhod.");
        }
        return new Stereo(input.rate, left, right);
    }

    private static void checkSamples(Stereo expected, Stereo result, String name) throws IOException {
        if (expected.rate != result.rate || expected.left.length != result.left.length) {
            throw new IOException(name + " se z referenco ne ujema po frekvenci ali številu vzorcev.");
        }
        int leftPeak = 0;
        int rightPeak = 0;
        for (int frame = 0; frame < expected.left.length; frame++) {
            if (result.left[frame] != result.right[frame]) {
                throw new IOException(name + " nima enakih izhodnih kanalov pri vzorcu " + frame + ".");
            }
            leftPeak = Math.max(leftPeak, pcmDifference(expected.left[frame], result.left[frame]));
            rightPeak = Math.max(rightPeak, pcmDifference(expected.right[frame], result.right[frame]));
        }
        System.out.printf("%s, največja razlika PCM L = %d, R = %d%n", name, leftPeak, rightPeak);
        if (leftPeak > TOLERANCE || rightPeak > TOLERANCE) {
            throw new IOException(name + " presega dovoljeni dve enoti 16-bitnega zapisa PCM.");
        }
    }

    private static int pcmDifference(float expected, float actual) {
        return Math.abs(Math.round(expected * PCM_SCALE) - Math.round(actual * PCM_SCALE));
    }

    private static Stereo read(Path path) throws IOException, UnsupportedAudioFileException {
        try (var file = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat format = file.getFormat();
            if (format.getChannels() != 2 || format.getSampleSizeInBits() != 16 || format.isBigEndian()
                    || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
                throw new IOException(path + " mora biti stereo WAV s 16-bitnim PCM.");
            }
            ByteBuffer pcm = ByteBuffer.wrap(file.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            if (pcm.remaining() == 0 || pcm.remaining() != file.getFrameLength() * 4L) {
                throw new IOException(path + " nima popolnih podatkov PCM.");
            }
            float[] left = new float[pcm.remaining() / 4];
            float[] right = new float[left.length];
            for (int frame = 0; frame < left.length; frame++) {
                left[frame] = pcm.getShort() / (float) PCM_SCALE;
                right[frame] = pcm.getShort() / (float) PCM_SCALE;
            }
            return new Stereo(format.getSampleRate(), left, right);
        }
    }

    private record Stereo(float rate, float[] left, float[] right) { }
}
