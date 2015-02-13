package de.uniba.dsg.bpmnspector.fix.seqflow;

import org.jdom2.*;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.located.LocatedJDOMFactory;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Matthias Geiger
 */
public class SequenceFlowSolver {

    public static final String BPMNNAMESPACE_STRING = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    public static final String SEQ_FLOW = "sequenceFlow";
    public static final String INCOMING = "incoming";
    public static final String OUTGOING = "outgoing";
    public static final String SOURCE_REF = "sourceRef";
    public static final String TARGET_REF = "targetRef";
    public static final String ID = "id";

    public static final Namespace BPMNNAMESPACE = Namespace.getNamespace(BPMNNAMESPACE_STRING);

    private static final Logger LOGGER = LoggerFactory
            .getLogger(SequenceFlowSolver.class.getSimpleName());


    private final SAXBuilder builder;

    public SequenceFlowSolver() {
        builder = new SAXBuilder();
    }

    public void fixSequenceFlowFaults(Path path) throws IOException {
        Document doc = loadFile(path);

        // get all SequenceFlow elements
        List<Element> allSequenceFlows = getAllSequenceFlows(doc);

        LOGGER.info("AllSequenceFlows size:" + allSequenceFlows.size());

        // get all Elements with IDs to be used in the following checks
        Map<String, Element> allElementsWithIds = createIdElementMap(doc);

        // check for each SequenceFlow element whether the referenced element cross reference the SeqFlow
        // if not - add incoming/outgoing sub element
        for(Element seqFlow : allSequenceFlows) {
            LOGGER.info("Trying to find elems for seqFlow with ID"+seqFlow.getAttributeValue(ID));
            // get element referenced by sourceRef attribute
            String sourceRef = seqFlow.getAttributeValue(SOURCE_REF);
            if(sourceRef != null && allElementsWithIds.get(sourceRef) != null) {
                Element elem = allElementsWithIds.get(sourceRef);
                // check element regarding outgoing subelement
                if(!elem.getChildren(OUTGOING, BPMNNAMESPACE).stream()
                            .anyMatch(e -> seqFlow.getAttributeValue(ID).equals(e.getText()))) {
                    // if no element contains seqFlow ID
                    // add new element with ID
                    LOGGER.info("Adding Element outgoing");
                    Element outgoing = new Element(OUTGOING, BPMNNAMESPACE);
                    outgoing.setText(seqFlow.getAttributeValue(ID));
                    elem.addContent(outgoing);
                }
            } else {
                // throw exception: either sourceRef attribute or referenced element does not exist
            }

            String targetRef = seqFlow.getAttributeValue(TARGET_REF);
            if(targetRef != null && allElementsWithIds.get(targetRef) != null) {
                Element elem = allElementsWithIds.get(targetRef);
                // check element regarding outgoing subelement
                if(!elem.getChildren(INCOMING, BPMNNAMESPACE).stream()
                        .anyMatch(e -> seqFlow.getAttributeValue(ID).equals(e.getText()))) {
                    // if no element contains seqFlow ID
                    // add new element with ID
                    LOGGER.info("Adding Element incoming");
                    Element incoming = new Element(INCOMING, BPMNNAMESPACE);
                    incoming.setText(seqFlow.getAttributeValue(ID));
                    elem.addContent(incoming);
                }
            } else {
                // throw exception: either sourceRef attribute or referenced element does not exist
            }
        }

        // save fixed file
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(doc, new FileOutputStream(Paths.get(path.toString()+"_fixed").toFile()));
    }

    private Document loadFile(Path path) {
        builder.setJDOMFactory(new LocatedJDOMFactory());

        if (Files.notExists(path) || !Files.isRegularFile(path)) {
            String msg = "Path " + path + " is invalid.";
            throw new IllegalArgumentException(msg);
        } else {
            try {
                Document processAsDoc = builder.build(path.toFile());
                if ("definitions".equals(processAsDoc.getRootElement().getName()) &&
                        BPMNNAMESPACE_STRING.equals(processAsDoc.getRootElement().getNamespaceURI())) {
                    return processAsDoc;
                } else {
                    throw new IllegalArgumentException("File is not a valid BPMN file.");
                }
            } catch (JDOMException | IOException e) {
                LOGGER.error("File could not be processed.", e);
                throw new IllegalArgumentException("File is not a valid BPMN file.");
            }
        }
    }

    private Map<String, Element> createIdElementMap(Document document) {
        Map<String, Element> mapOfAllElementsWithId = new ConcurrentHashMap<>();

        List<Element> allElems = new ArrayList<>();

        Iterator<Element> it = document.getRootElement().getDescendants(Filters.element(BPMNNAMESPACE));

        while (it.hasNext()) {
            allElems.add(it.next());
        }

        allElems.stream().parallel().filter(elem -> BPMNNAMESPACE.equals(elem.getNamespace()))
                .filter(elem -> elem.getAttribute("id")!=null)
                .forEach(elem -> mapOfAllElementsWithId.put(elem.getAttributeValue("id"), elem));

        return mapOfAllElementsWithId;
    }

    private List<Element> getAllSequenceFlows(Document document) {
        List<Element> allElems = new ArrayList<>();
        Iterator<Element> it = document.getRootElement().getDescendants(Filters.element(SEQ_FLOW, BPMNNAMESPACE));

        while (it.hasNext()) {
            allElems.add(it.next());
        }

        return allElems;
    }

}
