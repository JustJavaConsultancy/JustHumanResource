package com.justjava.humanresource.payroll.workflow.delegates;

import com.justjava.humanresource.payroll.service.PayrollPaymentService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("initiateBankTransferDelegate")
@RequiredArgsConstructor
public class InitiateBankTransferDelegate implements JavaDelegate {

    private final PayrollPaymentService paymentService;

    @Override
    public void execute(DelegateExecution execution) {

        Long companyId = (Long) execution.getVariable("companyId");
        LocalDate start = (LocalDate) execution.getVariable("periodStart");
        LocalDate end = (LocalDate) execution.getVariable("periodEnd");

        paymentService.initiateBulkPayments(companyId,execution.getProcessInstanceId(), start, end);
    }
}