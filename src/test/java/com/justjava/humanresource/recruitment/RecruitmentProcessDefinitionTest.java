package com.justjava.humanresource.recruitment;

import org.junit.jupiter.api.Test;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RecruitmentProcessDefinitionTest {
    private static final String NS="http://www.omg.org/spec/BPMN/20100524/MODEL";
    @Test void jobOpeningProcessHasPreparationReviewAndPublication() throws Exception {
        var d=parse("recruitmentJobOpeningProcess.bpmn");
        assertEquals("recruitmentJobOpeningProcess",d.getElementsByTagNameNS(NS,"process").item(0).getAttributes().getNamedItem("id").getNodeValue());
        assertIds(d,"userTask",Set.of("prepareJobOpening","reviewJobOpening"));
        assertIds(d,"serviceTask",Set.of("publish","cancel"));
    }
    @Test void applicationProcessCoversScreeningThroughPreEmployment() throws Exception {
        var d=parse("candidateApplicationProcess.bpmn");
        assertIds(d,"userTask",Set.of("screeningTask","assessmentTask","interviewTask","checksTask","offerTask","preEmploymentTask"));
        var services=d.getElementsByTagNameNS(NS,"serviceTask");
        for(int i=0;i<services.getLength();i++) assertEquals("${processRecruitmentDecisionDelegate}",services.item(i).getAttributes().getNamedItemNS("http://flowable.org/bpmn","delegateExpression").getNodeValue());
    }
    @Test void employmentOfferProcessRequiresApprovalAndExplicitOutcome() throws Exception {
        var d=parse("employmentOfferProcess.bpmn");
        assertIds(d,"userTask",Set.of("approveEmploymentOffer"));
        assertIds(d,"serviceTask",Set.of("approveOffer","rejectOffer"));
        assertIds(d,"endEvent",Set.of("offerApproved","offerRejected"));
    }
    private org.w3c.dom.Document parse(String name)throws Exception{try(var in=getClass().getResourceAsStream("/processes/"+name)){assertNotNull(in);var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);return f.newDocumentBuilder().parse(in);}}
    private void assertIds(org.w3c.dom.Document d,String type,Set<String> expected){var nodes=d.getElementsByTagNameNS(NS,type);java.util.Set<String> ids=new java.util.HashSet<>();for(int i=0;i<nodes.getLength();i++)ids.add(nodes.item(i).getAttributes().getNamedItem("id").getNodeValue());assertTrue(ids.containsAll(expected));}
}
