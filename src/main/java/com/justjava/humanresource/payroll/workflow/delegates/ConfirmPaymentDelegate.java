package com.justjava.humanresource.payroll.workflow.delegates;

import com.justjava.humanresource.payroll.service.PayrollPaymentService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("confirmPaymentDelegate")
@RequiredArgsConstructor
public class ConfirmPaymentDelegate implements JavaDelegate {

    private final PayrollPaymentService paymentService;

    @Override
    public void execute(DelegateExecution execution) {

        Long companyId = (Long) execution.getVariable("companyId");

        paymentService.confirmPaymentsAndNotifyFlowable(
                companyId,
                execution.getProcessInstanceId()
        );
    }
}