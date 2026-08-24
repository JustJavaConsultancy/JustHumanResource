package com.justjava.humanresource.payroll.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.payroll.service.PayrollPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PayrollPaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Value("${paystack.secret.key}")
    private String paystackSecretKey;

    @PostMapping
    public ResponseEntity<?> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {

        if (!validSignature(rawPayload, signature)) {
            return ResponseEntity.status(401).body("Invalid webhook signature");
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawPayload, new TypeReference<>() {});
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body("Invalid JSON payload");
        }

        // Paystack sends data inside a "data" object
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data == null) {
            return ResponseEntity.badRequest().body("Missing data object");
        }

        String reference = (String) data.get("reference");
        String status = (String) data.get("status");

        if (reference == null) {
            return ResponseEntity.badRequest().body("Missing reference");
        }
        if (!"success".equalsIgnoreCase(status) && !"failed".equalsIgnoreCase(status)) {
            return ResponseEntity.badRequest().body("Unsupported payment status");
        }

        paymentService.recordPaymentResult(reference, status, (String) data.get("reason"));
        return ResponseEntity.ok().build();

    }

    private boolean validSignature(String payload, String signature) {
        if (signature == null || signature.isBlank() || paystackSecretKey == null || paystackSecretKey.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(paystackSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature.trim());
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) {
            return false;
        }
    }
}
