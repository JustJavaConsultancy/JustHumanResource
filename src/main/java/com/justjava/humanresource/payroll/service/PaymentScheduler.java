package com.justjava.humanresource.payroll.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableScheduling
public class PaymentScheduler {
    private final PaymentRetryService retryService;
    private final PayrollPaymentService paymentService;
    /**
     * Retry failed payments
     */
    @Scheduled(fixedDelay = 60000) // every 1 minute
    public void retryFailedPayments() {
        retryService.retryFailedPayments();
    }

    /**
     * Process pending payments in batches
     */
    @Scheduled(fixedDelay = 30000) // every 30 seconds
    public void processPendingPayments() {
        paymentService.processPendingPayments();
    }
}