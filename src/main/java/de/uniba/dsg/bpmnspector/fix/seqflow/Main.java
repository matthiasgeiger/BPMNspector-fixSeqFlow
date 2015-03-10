package de.uniba.dsg.bpmnspector.fix.seqflow;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Matthias Geiger
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getSimpleName());

    /**
     * Starts SequenceFlow analysis and fixing process.
     *
     * A single argument is needed for execution:
     *      - absolute or relative path to a single BPMN file OR
     *      - absolute or relative path to a directory
     *
     * If the argument refers to a directory each file in this directory and all subdirectories with suffix .bpmn,
     * .bpmn2, or bpmn20.xml will be checked
     *
     * @param args - args[0] - file or directy to be analysed and fixed
     */
    public static void main(String[] args) {

        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);

        if(args.length!=1) {
            LOGGER.error("Invalid usage. Exactly one argument needed: Path to a single BPMN file or to a directory.");
        }

        SequenceFlowSolver solver = new SequenceFlowSolver();

        try {
            Path path = Paths.get(args[0]);
            if(Files.notExists(path)) {
                LOGGER.error("File does not exist");
            } else if(Files.isRegularFile(path)) {
                solver.fixSequenceFlowFaults(path);
            } else if (Files.isDirectory(path)) {
                List<Path> allBpmnFiles = getAllBpmnFileFromDirectory(path);
                for (Path singleFile : allBpmnFiles) {
                    try {
                        solver.fixSequenceFlowFaults(singleFile);
                    } catch (IllegalArgumentException e) {
                        LOGGER.error("Failed to fix: "+singleFile.toString(), e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failure during analysis", e);
        }
    }

    private static List<Path> getAllBpmnFileFromDirectory(Path directory) {
        List<Path> bpmnFiles = new ArrayList<>();
        String[] suffixes = {"bpmn", "bpmn2", "bpmn20.xml"};
        Collection<File> filesColl = org.apache.commons.io.FileUtils.listFiles(directory.toFile(), suffixes, true);
        bpmnFiles.addAll(filesColl.stream().map(File::toPath).collect(Collectors.toList()));
        return bpmnFiles;
    }
}
