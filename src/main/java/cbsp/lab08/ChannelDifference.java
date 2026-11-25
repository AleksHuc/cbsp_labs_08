package cbsp.lab08;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Add;

final class ChannelDifference {
    private ChannelDifference() { }

    static UnitOutputPort build(Synthesizer synth, UnitOutputPort stereo) {
        Add difference = new Add();
        synth.add(difference);

        // TODO (Task 2): connect the scaled channels to difference and return its output port.
        throw new UnsupportedOperationException("TODO (Task 2): build the stereo channel difference");
    }
}
