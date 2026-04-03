package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.dto.BankTransferRequest;
import com.justjava.humanresource.payroll.entity.PayrollPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BankIntegrationService {

    private final PaymentGatewayResolver resolver;

    private static final String DEFAULT_GATEWAY = "PAYSTACK";

    public String initiateTransfer(
            String accountNumber,
            String accountName,
            String bankName,
            BigDecimal amount,
            String reference
    ) {

        PaymentGateway gateway = resolver.resolve(DEFAULT_GATEWAY);

        return gateway.initiateTransfer(
                BankTransferRequest.builder()
                        .accountNumber(accountNumber)
                        .accountName(accountName)
                        .bankName(bankName)
                        .amount(amount)
                        .reference(reference)
                        .build()
        );
    }

    public void initiateBulkTransfers(List<PayrollPayment> payments) {

        PaymentGateway gateway = resolver.resolve(DEFAULT_GATEWAY);

        List<BankTransferRequest> requests = payments.stream()
                .map(p -> BankTransferRequest.builder()
                        .accountNumber(p.getAccountNumber())
                        .accountName(p.getAccountName())
                        .bankName(p.getBankName())
                        .amount(p.getAmount())
                        .reference(p.getExternalReference())
                        .build()
                )
                .toList();

        gateway.initiateBulkTransfer(requests);
    }
}