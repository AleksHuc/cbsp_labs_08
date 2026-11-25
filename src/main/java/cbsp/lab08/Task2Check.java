package cbsp.lab08;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

final class Task2Check {
    private Task2Check() { }

    static void run(Path media, Path outputDirectory)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        check(media.resolve("enaka-kanala.wav"), outputDirectory.resolve("task2-enaka-kanala.wav"));
        check(media.resolve("kontrolna-mesanica.wav"), outputDirectory.resolve("task2-kontrolna-mesanica.wav"));
    }

    private static void check(Path input, Path output)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        JsynRender.render(input, output);

        // The two bundled controls use signed little-endian 16-bit stereo PCM.
        try (var source = AudioSystem.getAudioInputStream(input.toFile());
             var result = AudioSystem.getAudioInputStream(output.toFile())) {
            if (!source.getFormat().matches(result.getFormat())
                    || source.getFrameLength() != result.getFrameLength()) {
                throw new IOException(output + " nima enake frekvence, formata ali števila vzorcev kot vhod.");
            }
            ByteBuffer expected = ByteBuffer.wrap(source.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer rendered = ByteBuffer.wrap(result.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            if (expected.remaining() != source.getFrameLength() * 4L
                    || rendered.remaining() != expected.remaining()) {
                throw new IOException(output + " nima popolnih podatkov PCM.");
            }
            for (int frame = 0; expected.hasRemaining(); frame++) {
                int difference = expected.getShort() - expected.getShort();
                int left = rendered.getShort();
                int right = rendered.getShort();
                int tolerance = difference == 0 ? 0 : 1;
                if (left != right || Math.abs(left - difference / 2.0) > tolerance) {
                    throw new IOException(input.getFileName() + ": nepravilna razlika pri vzorcu " + frame + ".");
                }
            }
            System.out.printf("%s → %s (%d vzorcev na kanal, preverjeno)%n",
                    input.getFileName(), output, result.getFrameLength());
        }
    }
}
