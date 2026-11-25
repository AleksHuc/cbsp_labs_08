package cbsp.lab08;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.data.FloatSample;
import com.jsyn.devices.javasound.JavaSoundAudioDevice;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.FixedRateStereoReader;
import com.jsyn.util.SampleLoader;
import com.jsyn.util.TransportListener;
import com.jsyn.util.TransportModel;
import com.jsyn.util.WaveRecorder;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

final class JsynRender {
    private JsynRender() { }

    static void render(Path input, Path output)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        render(input, output, null);
    }

    static void render(Path input, Path output, BassBlend.Settings blend)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        render(input, output, blend, false);
    }

    static FloatSample renderRoundTrip(Path input, Path output)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        return render(input, output, null, true);
    }

    private static FloatSample render(Path input, Path output, BassBlend.Settings blend, boolean roundTrip)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        if (Files.exists(output) && Files.isSameFile(input, output)) {
            throw new IOException("Vhod in izhod morata biti različni datoteki.");
        }
        FloatSample sample = loadStereoWav(input);
        int rate = (int) sample.getFrameRate();
        if (blend != null && blend.cutoffHz() > 0.499 * rate) {
            throw new IllegalArgumentException("Mejna frekvenca mora biti največ 0,499-kratnik frekvence vzorčenja.");
        }
        Path directory = output.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "jsyn-", ".wav");
        try {
            record(sample, rate, temporary, blend, roundTrip);
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return sample;
    }

    static FloatSample loadStereoWav(Path input) throws IOException, UnsupportedAudioFileException {
        AudioFileFormat file = AudioSystem.getAudioFileFormat(input.toFile());
        AudioFormat format = file.getFormat();
        int bits = format.getSampleSizeInBits();
        boolean integerPcm = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && (bits == 16 || bits == 24 || bits == 32);
        boolean floatPcm = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding()) && bits == 32;
        if (!AudioFileFormat.Type.WAVE.equals(file.getType()) || format.getChannels() != 2
                || !(integerPcm || floatPcm)) {
            throw new IOException("Uporabite stereo WAV s 16-, 24- ali 32-bitnim celoštevilskim PCM ali 32-bitnim plavajočim PCM.");
        }

        FloatSample sample;
        try (var stream = new BufferedInputStream(Files.newInputStream(input))) {
            sample = SampleLoader.loadFloatSample(stream);
        }
        double rate = sample.getFrameRate();
        if (rate <= 0 || rate > Integer.MAX_VALUE || rate != (int) rate || sample.getNumFrames() == 0) {
            throw new IOException("Vhodni WAV mora imeti veljavno frekvenco vzorčenja in vsebovati zvok.");
        }
        return sample;
    }

    private static void record(FloatSample sample, int rate, Path output, BassBlend.Settings blend,
            boolean roundTrip)
            throws IOException, InterruptedException {
        Synthesizer synth = JSyn.createSynthesizer(new JavaSoundAudioDevice());
        synth.setRealTime(false);
        // WaveRecorder takes its WAV sample rate from the running synthesizer.
        synth.start(rate, -1, 0, -1, 0);
        try {
            FixedRateStereoReader reader = new FixedRateStereoReader();
            synth.add(reader);
            WaveRecorder recorder = new WaveRecorder(synth, output.toFile());
            try {
                if (roundTrip) {
                    JsynRoundTrip.connectStereo(reader, recorder);
                } else {
                    UnitOutputPort signal = ChannelDifference.build(synth, reader.output);
                    if (blend != null) {
                        signal = BassBlend.build(synth, reader.output, signal, blend);
                    }
                    signal.connect(0, recorder.getInput(), 0);
                    signal.connect(0, recorder.getInput(), 1);
                }
                JsynRoundTrip.queueSample(sample, reader);

                int frames = sample.getNumFrames();
                recorder.setMaxRecordingTime(Math.nextUp(frames / (double) rate));
                BlockingQueue<Long> positions = new LinkedBlockingQueue<>();
                recorder.addTransportListener(new TransportListener() {
                    @Override
                    public void positionChanged(TransportModel model, long position) {
                        if (position > 0) {
                            positions.add(position);
                        }
                    }

                    @Override
                    public void stateChanged(TransportModel model, int state) { }
                });
                recorder.start();

                // Advance in batches smaller than the recorder FIFO. If writing fails,
                // the producer can report the failure instead of blocking on a full FIFO.
                long written = 0;
                while (written < frames) {
                    long next = Math.min(written + 8192, frames);
                    synth.sleepUntil(Math.nextUp(next / (double) rate));
                    while (written < next) {
                        Long position = positions.poll(5, TimeUnit.SECONDS);
                        if (position == null) {
                            throw new IOException("Snemalnik JSyn ni dokončal izvoza WAV.");
                        }
                        written = position;
                    }
                }
            } finally {
                recorder.close();
            }
        } finally {
            synth.stop();
        }
    }
}
