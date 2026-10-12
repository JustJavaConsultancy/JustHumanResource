package com.justjava.humanresource.promotion;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionBpmnDefinitionTest {

    @Test
    void definesTheExpectedApprovalTasksAndOutcomes() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream input = getClass().getResourceAsStream(
                "/processes/promotion-approval-process.bpmn20.xml")) {
            assertThat(input).isNotNull();
            Document document = factory.newDocumentBuilder().parse(input);
            Element process = (Element) document.getElementsByTagNameNS("*", "process").item(0);
            assertThat(process.getAttribute("id")).isEqualTo("promotionApprovalProcess");

            Set<String> taskIds = ids(document.getElementsByTagNameNS("*", "userTask"));
            assertThat(taskIds).containsExactlyInAnyOrder(
                    "lineManagerApproval", "hrApproval", "financeApproval", "executiveApproval");

            Set<String> serviceTaskIds = ids(document.getElementsByTagNameNS("*", "serviceTask"));
            assertThat(serviceTaskIds).contains(
                    "initialize", "evaluateEligibility", "recordDecision", "approved", "rejected", "returned");
        }
    }

    @Test
    void isAcceptedByTheFlowableBpmnConverter() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/processes/promotion-approval-process.bpmn20.xml")) {
            assertThat(input).isNotNull();
            var model = new BpmnXMLConverter().convertToBpmnModel(
                    new InputStreamSource(input), true, true);
            assertThat(model.getMainProcess().getId()).isEqualTo("promotionApprovalProcess");
            assertThat(model.getMainProcess().getFlowElement("recordDecision")).isNotNull();
        }
    }

    private Set<String> ids(NodeList nodes) {
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            ids.add(((Element) nodes.item(index)).getAttribute("id"));
        }
        return ids;
    }
}
