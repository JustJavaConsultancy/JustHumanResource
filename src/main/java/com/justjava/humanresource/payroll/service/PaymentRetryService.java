package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.dto.PaymentStatus;
import com.justjava.humanresource.payroll.entity.PayrollPayment;
import com.justjava.humanresource.payroll.repositories.PayrollPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentRetryService {

    private final PayrollPaymentRepository repository;
    private final BankIntegrationService bankService;

    private static final int MAX_RETRIES = 3;

    @Transactional
    public void retryFailedPayments() {

        List<PayrollPayment> failed = repository.findRetryablePayments();

        for (PayrollPayment p : failed) {

            if (p.getRetryCount() >= MAX_RETRIES) {
                continue;
            }

            try {
                String ref = bankService.initiateTransfer(
                        p.getAccountNumber(),
                        p.getAccountName(),
                        p.getBankName(),
                        p.getAmount(),
                        "RETRY-" + p.getId()
                );

                p.setExternalReference(ref);
                p.setStatus(PaymentStatus.PROCESSING);

            } catch (Exception ex) {

                int retry = p.getRetryCount() + 1;
                p.setRetryCount(retry);
                p.setLastTriedAt(LocalDateTime.now());

                // Exponential backoff: 2^retry minutes
                long delayMinutes = (long) Math.pow(2, retry);

                p.setNextRetryAt(LocalDateTime.now().plusMinutes(delayMinutes));
                p.setFailureReason(ex.getMessage());
            }
        }
    }

}