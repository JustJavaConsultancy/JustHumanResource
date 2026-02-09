package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class InitializePayrollDelegate implements JavaDelegate {

    private final PayrollOrchestrationService payrollService;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId =
                (Long) execution.getVariable("employeeId");

        LocalDate payrollDate =
                (LocalDate) execution.getVariable("payrollDate");

        String processInstanceId =
                execution.getProcessInstanceId();

        Long payrollRunId =
                payrollService.initializePayrollRun(
                        employeeId,
                        payrollDate,
                        processInstanceId
                );

        execution.setVariable("payrollRunId", payrollRunId);
    }
}
