package com.justjava.humanresource.request.workflow;
import org.junit.jupiter.api.Test;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.*;
class GenericRequestProcessDefinitionTest {
 @Test void processDefinitionIsWellFormedAndHasExpectedProcessId() throws Exception {try(var in=getClass().getResourceAsStream("/processes/genericRequestApprovalProcess.bpmn")){assertNotNull(in);var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);var document=factory.newDocumentBuilder().parse(in);var processes=document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL","process");assertEquals(1,processes.getLength());assertEquals("genericRequestApprovalProcess",processes.item(0).getAttributes().getNamedItem("id").getNodeValue());}}
}
