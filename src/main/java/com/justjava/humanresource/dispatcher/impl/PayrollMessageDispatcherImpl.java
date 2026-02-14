package com.justjava.humanresource.dispatcher.impl;

import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.payroll.workflow.EmployeePayrollProcessManager;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollMessageDispatcherImpl
        implements PayrollMessageDispatcher {

    private final RuntimeService runtimeService;
    private final EmployeePayrollProcessManager processManager;

    @Override
    public void requestPayroll(
            Long employeeId,
            LocalDate effectiveDate) {

        processManager.ensureProcessStarted(employeeId);
/*
        if(true)
            return;
*/

        String businessKey = "EMPLOYEE_" + employeeId;

        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processDefinitionKey("employeePayrollSupervisor")
                .processInstanceBusinessKey(businessKey)
                .singleResult();



        Execution execution = runtimeService
                .createExecutionQuery()
                .processInstanceId(processInstance.getId())
                .messageEventSubscriptionName("PAYROLL_MESSAGE")
                .singleResult();

        if (execution == null) {
            throw new IllegalStateException(
                    "No waiting execution found for businessKey: " + businessKey
            );
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("employeeId", employeeId);
        vars.put("payrollDate", effectiveDate);
        vars.put("approvalRequired", false);
        vars.put("exit", false);

        runtimeService.messageEventReceived(
                "PAYROLL_MESSAGE",
                execution.getId(),
                vars
        );
    }
}