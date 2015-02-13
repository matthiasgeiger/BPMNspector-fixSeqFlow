package de.uniba.dsg.bpmnspector.fix.seqflow;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author Matthias Geiger
 */
public class Main {

    /**
     *
     * @param args - args[0] - file to be fixed
     */
    public static void main(String[] args) {
        SequenceFlowSolver solver = new SequenceFlowSolver();
        try {
            solver.fixSequenceFlowFaults(Paths.get(args[0]));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
