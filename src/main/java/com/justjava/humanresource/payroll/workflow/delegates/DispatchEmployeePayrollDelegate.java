package com.justjava.humanresource.payroll.workflow.delegates;

import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("dispatchEmployeePayrollDelegate")
@RequiredArgsConstructor
public class DispatchEmployeePayrollDelegate implements JavaDelegate {

    private final PayrollMessageDispatcher dispatcher;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");
        LocalDate payrollDate =
                (LocalDate) execution.getVariable("periodEnd");

        dispatcher.requestPayroll(employeeId, payrollDate);
    }
}