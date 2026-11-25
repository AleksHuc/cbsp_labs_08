package cbsp.lab08;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Add;

final class BassBlend {
    static final double BUTTERWORTH_Q = 1.0 / Math.sqrt(2.0);
    static final Settings DEFAULT = new Settings(180.0, 0.5);

    record Settings(double cutoffHz, double gain) {
        Settings {
            if (!Double.isFinite(cutoffHz) || cutoffHz < 0.00001) {
                throw new IllegalArgumentException("Mejna frekvenca mora biti končno število in vsaj 0,00001 Hz.");
            }
            if (!Double.isFinite(gain) || gain < 0) {
                throw new IllegalArgumentException("Ojačenje mora biti končno nenegativno število.");
            }
        }
    }

    private BassBlend() { }

    static UnitOutputPort build(Synthesizer synth, UnitOutputPort stereo,
            UnitOutputPort difference, Settings settings) {
        Add output = new Add();
        synth.add(output);
        difference.connect(output.inputA);

        // TODO (Task 3): filter and scale the channel average, connect it to output.inputB and return output.output.
        throw new UnsupportedOperationException("TODO (Task 3): build the low-frequency blend");
    }
}
