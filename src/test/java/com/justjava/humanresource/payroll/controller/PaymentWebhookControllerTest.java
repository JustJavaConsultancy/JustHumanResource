package com.justjava.humanresource.payroll.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.payroll.service.PayrollPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentWebhookControllerTest {
    private static final String SECRET = "test-secret";
    private PayrollPaymentService paymentService;
    private PaymentWebhookController controller;

    @BeforeEach
    void setUp() {
        paymentService = mock(PayrollPaymentService.class);
        controller = new PaymentWebhookController(paymentService, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "paystackSecretKey", SECRET);
    }

    @Test
    void validSignedSuccessWebhook_shouldRecordPaymentResult() throws Exception {
        String payload = "{\"event\":\"transfer.success\",\"data\":{\"reference\":\"PAY-44\",\"status\":\"success\"}}";

        var response = controller.handleWebhook(payload, signature(payload));

        assertEquals(200, response.getStatusCode().value());
        verify(paymentService).recordPaymentResult("PAY-44", "success", null);
    }

    @Test
    void invalidSignature_shouldRejectWebhookWithoutChangingPayment() {
        String payload = "{\"data\":{\"reference\":\"PAY-44\",\"status\":\"success\"}}";

        var response = controller.handleWebhook(payload, "bad-signature");

        assertEquals(401, response.getStatusCode().value());
        verify(paymentService, never()).recordPaymentResult(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
