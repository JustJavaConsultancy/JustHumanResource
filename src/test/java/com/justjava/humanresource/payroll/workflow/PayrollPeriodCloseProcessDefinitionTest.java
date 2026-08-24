package com.justjava.humanresource.payroll.workflow;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollPeriodCloseProcessDefinitionTest {
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    @Test
    void approvedPayrollBranchesByTransferModeAndConvergesOnSuspenseClearing() throws Exception {
        try (var in = getClass().getResourceAsStream("/processes/payrollPeriodCloseProcess.bpmn")) {
            assertNotNull(in);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var document = factory.newDocumentBuilder().parse(in);

            assertDelegate(document, "ServiceTask_20", "${resolvePaymentModeDelegate}");
            assertDelegate(document, "ServiceTask_19", "${processPaymentsDelegate}");
            assertDelegate(document, "assumePaymentSuccess", "${assumePaymentSuccessDelegate}");
            assertDelegate(document, "clearPayrollSuspense", "${clearPayrollSuspenseDelegate}");
            assertDelegate(document, "verifyPayrollSuspense", "${verifyPayrollSuspenseDelegate}");

            Map<String, String> flows = sequenceFlows(document);
            assertEquals("ServiceTask_20->paymentModeGateway", flows.get("SequenceFlow_21"));
            assertEquals("paymentModeGateway->ServiceTask_19", flows.get("paymentModeEnabled"));
            assertEquals("paymentModeGateway->assumePaymentSuccess", flows.get("paymentModeDisabled"));
            assertEquals("IntermediateMessageEventCatching_10->clearPayrollSuspense", flows.get("gatewaySuccessToClearing"));
            assertEquals("assumePaymentSuccess->clearPayrollSuspense", flows.get("internalPaymentToClearing"));
            assertEquals("clearPayrollSuspense->verifyPayrollSuspense", flows.get("clearingToVerification"));
            assertEquals("verifyPayrollSuspense->EndNoneEvent_9", flows.get("verificationToEnd"));

            Element gateway = byId(document, "exclusiveGateway", "paymentModeGateway");
            assertEquals("paymentModeDisabled", gateway.getAttribute("default"));
            Element enabledFlow = byId(document, "sequenceFlow", "paymentModeEnabled");
            assertTrue(enabledFlow.getTextContent().contains("transferEnabled == true"));
        }
    }

    private void assertDelegate(org.w3c.dom.Document document, String id, String expression) {
        Element task = byId(document, "serviceTask", id);
        assertEquals(expression, task.getAttributeNS(FLOWABLE_NS, "delegateExpression"));
        assertTrue(task.getAttributeNS(FLOWABLE_NS, "expression").isEmpty());
    }

    private Map<String, String> sequenceFlows(org.w3c.dom.Document document) {
        Map<String, String> flows = new HashMap<>();
        var nodes = document.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element flow = (Element) nodes.item(i);
            flows.put(flow.getAttribute("id"), flow.getAttribute("sourceRef") + "->" + flow.getAttribute("targetRef"));
        }
        return flows;
    }

    private Element byId(org.w3c.dom.Document document, String localName, String id) {
        var nodes = document.getElementsByTagNameNS(BPMN_NS, localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        throw new AssertionError("Missing BPMN " + localName + " with id " + id);
    }
}
