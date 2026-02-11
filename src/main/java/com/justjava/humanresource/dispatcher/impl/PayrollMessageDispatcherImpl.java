package com.justjava.humanresource.dispatcher.impl;

import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.payroll.workflow.EmployeePayrollProcessManager;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
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

        Map<String, Object> vars = new HashMap<>();
        vars.put("employeeId", employeeId);
        vars.put("payrollDate", effectiveDate);
        vars.put("approvalRequired", true);

        runtimeService.messageEventReceived(
                "PAYROLL_REQUEST",
                "EMPLOYEE_" + employeeId,
                vars
        );
    }
}
