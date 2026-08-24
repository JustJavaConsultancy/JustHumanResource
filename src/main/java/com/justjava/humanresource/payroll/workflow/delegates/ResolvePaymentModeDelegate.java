package com.justjava.humanresource.payroll.workflow.delegates;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("resolvePaymentModeDelegate")
public class ResolvePaymentModeDelegate implements JavaDelegate {

    @Value("${payroll.payment.enable-transfer:false}")
    private boolean transferEnabled;

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("transferEnabled", transferEnabled);
    }
}
