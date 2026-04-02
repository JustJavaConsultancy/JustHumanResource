package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.payroll.dto.BankTransferRequest;
import com.justjava.humanresource.payroll.dto.PaymentStatus;
import com.justjava.humanresource.payroll.service.PaymentGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PaystackPaymentGateway implements PaymentGateway {

    @Override
    public String getName() {
        return "PAYSTACK";
    }

    @Override
    public String initiateTransfer(BankTransferRequest request) {

        // Call Paystack API here
        return "PS-" + UUID.randomUUID();
    }

    @Override
    public void initiateBulkTransfer(List<BankTransferRequest> requests) {

        // Batch API call (important)
        System.out.println("Processing " + requests.size() + " bulk transfers");
    }

    @Override
    public PaymentStatus checkStatus(String reference) {
        return PaymentStatus.SUCCESS;
    }
}