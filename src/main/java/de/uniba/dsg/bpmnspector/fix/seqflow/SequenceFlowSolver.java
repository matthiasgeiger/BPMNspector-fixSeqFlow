package de.uniba.dsg.bpmnspector.fix.seqflow;

import org.jdom2.*;
import org.jdom2.filter.Filter;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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


    /**
     * Checks whether a BPMN file determined by the path uses the correct sequenceFlow referencing paradigm
     * defined in the BPMN spec. I.e., each element referenced by a SequenceFlow using the sourceRef or targetRef
     * attribute must cross reference the sequenceFlow using an incoming resp. outgoing sub element.
     * <p>
     * If the BPMN process violates this constraint, a new File with proposed fixes is created. It is stored in the
     * folder of the original file and prefixed with &quot;fixed_&quot;
     *
     * @param path the path of the BPMN file
     * @throws IOException              thrown if the creation of the fixed file fails
     * @throws IllegalArgumentException thrown if the file at path is not a valid BPMN file
     */
    public void fixSequenceFlowFaults(Path path) throws IOException, IllegalArgumentException {

        LOGGER.info("Checking file "+path.toString());
        Document doc = loadFile(path);

        // get all SequenceFlow elements
        List<Element> allSequenceFlows = getAllElementsByFilter(doc, Filters.element(SEQ_FLOW, BPMNNAMESPACE));

        LOGGER.debug("Found " + allSequenceFlows.size() + " sequenceFlow elements.");

        // get all Elements with IDs to be used in the following checks
        Map<String, Element> allElementsWithIds = createIdElementMap(doc);

        boolean hasBeenChanged = false;

        // check for each SequenceFlow element whether the referenced element cross reference the SeqFlow
        // if not - add incoming/outgoing sub element
        for (Element seqFlow : allSequenceFlows) {
            if(seqFlow.getAttributeValue(ID)==null) {
                LOGGER.debug("Process is incorrect as attribute @id is missing for a SequenceFlow - generating a ID");
                createSeqFlowId(seqFlow, path);
            }
            LOGGER.debug("Checking sourceRef and targetRefs for seqFlow with ID " + seqFlow.getAttributeValue(ID));

            // Check whether element referenced by targetRef has the needed cross reference
            hasBeenChanged = hasBeenChanged | findAndCreateElementForSeqFlowAttribute(SOURCE_REF, OUTGOING, allElementsWithIds, seqFlow);

            // Check whether element referenced by targetRef has the needed cross reference
            hasBeenChanged = hasBeenChanged | findAndCreateElementForSeqFlowAttribute(TARGET_REF, INCOMING, allElementsWithIds, seqFlow);
        }


        if (hasBeenChanged) {
            // save fixed file
            LOGGER.info("File was incorrect. Creating new file with fixed sequenceFlow references.");
            Path newFile = path.getParent().resolve("fixed_" + path.getFileName().toString());
            XMLOutputter outputter = new XMLOutputter();
            outputter.setFormat(Format.getPrettyFormat());
            outputter.output(doc, new FileOutputStream(newFile.toFile()));
            LOGGER.info("File created: " + newFile.toString());
            // rename original file
            Files.move(path, path.getParent().resolve(path.getFileName().toString()+"_UNCORRECTED"));
        } else {
            LOGGER.info("File was correct. No reference fixes needed.");
        }
    }

    /**
     * Simple method which creates and adds an ID to a SequenceFlow Element
     *
     * @param seqFlow the flow to be changed
     * @param path the path of the file
     */
    private void createSeqFlowId(Element seqFlow, Path path) {
        seqFlow.setAttribute("id", "id_"+String.valueOf(Math.abs(seqFlow.hashCode()+path.hashCode())));
    }

    /**
     * Method to find and (potentially) create the needed incoming/outgoing sub element
     * <p>
     * Method checks whether the the element referenced by the attribute value of the attribute 'seqFlowAttribute' has a
     * subelement with the name 'elementName' which refers to the ID of the given 'seqFlow'.
     * <p>
     * If no such sub element can be found, the sub element is created.
     *
     * @param seqFlowAttribute   the sequenceFlow reference attribute to lookup (either sourceRef or targetRef)
     * @param elementName        the name of the element to lookup/create (either incoming or outgoing)
     * @param allElementsWithIds list of all elements with ids
     * @param seqFlow            the sequenceFlow element which refs should be checked
     * @return returns true if a element has been changed, false otherwise
     */
    private boolean findAndCreateElementForSeqFlowAttribute(String seqFlowAttribute, String elementName,
                                                            Map<String, Element> allElementsWithIds, Element seqFlow) {
        String attributeValue = seqFlow.getAttributeValue(seqFlowAttribute);
        if (attributeValue != null && allElementsWithIds.get(attributeValue) != null) {
            Element elem = allElementsWithIds.get(attributeValue);
            // check element regarding outgoing subelement
            if (!elem.getChildren(elementName, BPMNNAMESPACE).stream()
                    .anyMatch(e -> seqFlow.getAttributeValue(ID).equals(e.getText()))) {
                // if no element contains seqFlow ID
                // add new element with ID

                Element newSubElem = new Element(elementName, BPMNNAMESPACE);
                newSubElem.setText(seqFlow.getAttributeValue(ID));

                int index = determineIndexForElementInsertion(elem);
                LOGGER.debug("Determined index: "+index);
                if(index>elem.getChildren().size()) {
                    LOGGER.debug("Adding SubElement '" + elementName + "' to element '" + elem.getName() + "' with ID " + attributeValue+" as last SubElem");
                    elem.addContent(newSubElem);
                } else {
                    LOGGER.debug("Adding SubElement '" + elementName + "' to element '" + elem.getName() + "' with ID " + attributeValue+" at Pos."+index);
                    elem.addContent(index, newSubElem);
                }

                return true;
            }
            return false;
        } else {
            // throw exception: either sequenceFlow attribute or referenced element does not exist
            throw new IllegalArgumentException("attribute '" + seqFlowAttribute + "' or referenced element for sequenceFlow "
                    + seqFlow.getAttributeValue(ID) + " does not exist.");
        }
    }

    private Document loadFile(Path path) {

        if (Files.notExists(path) || !Files.isRegularFile(path)) {
            String msg = "Path " + path + " is invalid.";
            throw new IllegalArgumentException(msg);
        } else {
            SAXBuilder builder = new SAXBuilder();
            builder.setIgnoringBoundaryWhitespace(true);
            builder.setIgnoringElementContentWhitespace(true);
            builder.setJDOMFactory(new DefaultJDOMFactory());

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

        List<Element> allElems = getAllElementsByFilter(document, Filters.element(BPMNNAMESPACE));

        allElems.stream().filter(elem -> BPMNNAMESPACE.equals(elem.getNamespace()))
                .filter(elem -> elem.getAttribute("id") != null)
                .forEach(elem -> mapOfAllElementsWithId.put(elem.getAttributeValue("id"), elem));

        return mapOfAllElementsWithId;
    }

    private List<Element> getAllElementsByFilter(Document document, Filter<Element> filter) {
        List<Element> allElems = new ArrayList<>();
        Iterator<Element> it = document.getRootElement().getDescendants(filter);

        while (it.hasNext()) {
            allElems.add(it.next());
        }

        return allElems;
    }

    private int determineIndexForElementInsertion(Element parent) {
        // Caution: Order in list is important: new Elem must be placed after last incoming element - if not present:
        // after last categoryValueRef, etc.
        List<String> allowedElementsBefore = Arrays.asList("incoming", "categoryValueRef", "monitoring", "auditing", "extensionElements", "documentation");

        List<Element> children = parent.getChildren();

        for(String str : allowedElementsBefore) {
            List<Element> relevantElements = children.stream().filter(c -> c.getName().equals(str)).collect(Collectors.toList());
            if(!relevantElements.isEmpty()) {
                // use last element in list to determine index
                LOGGER.debug("Found elem: "+str+" at Index: "+parent.indexOf(relevantElements.get(relevantElements.size()-1)));
                return parent.indexOf(relevantElements.get(relevantElements.size()-1))+1;
            }
        }
        LOGGER.debug("No Elem found - returning 0 - newElem can be placed at first place.");
        return 0;
    }
}
