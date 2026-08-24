package com.justjava.humanresource.payroll.service;

import java.time.LocalDate;

public interface PayrollPaymentService {
    void initiateBulkPayments(Long companyId, Long payrollPeriodId, String processInstanceId, LocalDate periodStart, LocalDate periodEnd);
    void processPendingPayments();

    void markBatchSuccessful(String processInstanceId);

    void submitBatchToGateway(String processInstanceId);

    void clearSuspenseForBatch(String processInstanceId);

    void verifySuspenseClearedForBatch(String processInstanceId);

    void confirmPaymentsAndNotifyFlowable(Long companyId, String processInstanceId);

    void recordPaymentResult(String reference, String status, String failureReason);

    void confirmCompletedPayments();
}
