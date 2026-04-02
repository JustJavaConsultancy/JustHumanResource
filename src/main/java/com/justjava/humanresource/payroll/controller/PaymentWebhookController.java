package com.justjava.humanresource.payroll.controller;

import com.justjava.humanresource.payroll.dto.PaymentStatus;
import com.justjava.humanresource.payroll.entity.PayrollPayment;
import com.justjava.humanresource.payroll.repositories.PayrollPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PayrollPaymentRepository paymentRepository;
    private final RuntimeService runtimeService;

    @PostMapping
    @Transactional
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {

        String reference = (String) payload.get("reference");
        String status = (String) payload.get("status");

        if (reference == null) {
            return ResponseEntity.badRequest().body("Missing reference");
        }

        PayrollPayment payment = paymentRepository
                .findByExternalReference(reference)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));

        // ----------------------------------------------------
        // 1. UPDATE PAYMENT STATUS
        // ----------------------------------------------------

        if ("success".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.SUCCESS);
        } else if ("failed".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason((String) payload.get("reason"));
        }

        paymentRepository.save(payment);

        // ----------------------------------------------------
        // 2. CHECK IF ALL PAYMENTS COMPLETED
        // ----------------------------------------------------

        Long companyId = payment.getCompanyId();

        long stillProcessing =
                paymentRepository.countByCompanyIdAndStatusIn(
                        companyId,
                        List.of(
                                PaymentStatus.PENDING,
                                PaymentStatus.PROCESSING
                        )
                );

        long failed =
                paymentRepository.countByCompanyIdAndStatus(
                        companyId,
                        PaymentStatus.FAILED
                );

        // ----------------------------------------------------
        // 3. TRIGGER FLOWABLE (ONLY WHEN DONE)
        // ----------------------------------------------------

        if (stillProcessing == 0) {

            // You must store this earlier when starting process
            String processInstanceId = payment.getProcessInstanceId();//resolveProcessInstanceId(companyId);

            runtimeService.messageEventReceived(
                    "PAYMENT_MADE",
                    processInstanceId
            );
        }
        return ResponseEntity.ok().build();
    }
}