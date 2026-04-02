package com.justjava.humanresource.payroll.service;

import java.time.LocalDate;

public interface PayrollPaymentService {
    void initiateBulkPayments(Long companyId, String processInstanceId,LocalDate periodStart, LocalDate periodEnd);
    void processPendingPayments();

    void confirmPaymentsAndNotifyFlowable(Long companyId, String processInstanceId);
}