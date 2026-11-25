package cbsp.lab08;

import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class Main {
    private static final Path MEDIA = Path.of("media");
    private static final Path OUTPUT = Path.of("output");
    private static final String USAGE = """
            Uporaba
            --task2 [vhod.wav [izhod.wav]]
            --task3 [mejnaHz ojačenje [vhod.wav [izhod.wav]]]
            --task4
            --check1
            --check2
            --check3
            --check4""";

    private Main() { }

    public static void main(String[] args) {
        try {
            if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
                System.out.println(USAGE);
            } else if (args.length == 1 && args[0].equals("--check1")) {
                Task1Check.run(MEDIA.resolve("kontrolna-mesanica.wav"), OUTPUT.resolve("task1-round-trip.wav"));
            } else if (args.length <= 3 && args[0].equals("--task2")) {
                Path input = args.length >= 2 ? Path.of(args[1]) : MEDIA.resolve("skladba.wav");
                Path output = args.length == 3 ? Path.of(args[2]) : OUTPUT.resolve("task2-jsyn.wav");
                JsynRender.render(input, output);
                System.out.println("Izvoz: " + output);
            } else if (args.length == 1 && args[0].equals("--check2")) {
                Task2Check.run(MEDIA, OUTPUT);
            } else if (args[0].equals("--task3") && (args.length == 1 || args.length >= 3 && args.length <= 5)) {
                BassBlend.Settings blend = args.length == 1 ? BassBlend.DEFAULT
                        : new BassBlend.Settings(Double.parseDouble(args[1]), Double.parseDouble(args[2]));
                Path input = args.length >= 4 ? Path.of(args[3]) : MEDIA.resolve("skladba.wav");
                String defaultOutput = blend.gain() == 0 ? "task3-jsyn-zero.wav" : "task3-jsyn-low.wav";
                Path output = args.length == 5 ? Path.of(args[4]) : OUTPUT.resolve(defaultOutput);
                JsynRender.render(input, output, blend);
                System.out.println("Izvoz: " + output);
            } else if (args.length == 1 && args[0].equals("--check3")) {
                Task3Check.run(MEDIA.resolve("kontrolna-mesanica.wav"), OUTPUT);
            } else if (args.length == 1 && args[0].equals("--check4")) {
                Task4Check.run(MEDIA);
            } else if (args.length == 1 && args[0].equals("--task4")) {
                Task4Check.compareExports(OUTPUT);
            } else {
                throw new IllegalArgumentException("Za seznam ukazov uporabite --help.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Izvoz je bil prekinjen.");
            System.exit(1);
        } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException e) {
            System.err.println(e);
            System.exit(1);
        }
    }
}
