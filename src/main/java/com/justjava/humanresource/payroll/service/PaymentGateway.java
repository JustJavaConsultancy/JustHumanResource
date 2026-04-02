package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.dto.BankTransferRequest;
import com.justjava.humanresource.payroll.dto.PaymentStatus;

import java.util.List;

public interface PaymentGateway {

    String getName();

    String initiateTransfer(BankTransferRequest request);

    void initiateBulkTransfer(List<BankTransferRequest> requests);

    PaymentStatus checkStatus(String reference);
}