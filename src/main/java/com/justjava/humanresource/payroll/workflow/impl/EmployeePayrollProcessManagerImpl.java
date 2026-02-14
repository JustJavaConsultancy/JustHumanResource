package com.justjava.humanresource.payroll.workflow.impl;

import com.justjava.humanresource.payroll.workflow.EmployeePayrollProcessManager;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmployeePayrollProcessManagerImpl
        implements EmployeePayrollProcessManager {

    private final RuntimeService runtimeService;

    @Override
    public void ensureProcessStarted(Long employeeId) {

        String businessKey = businessKey(employeeId);

        ProcessInstance existing = runtimeService
                .createProcessInstanceQuery()
                .processDefinitionKey("employeePayrollSupervisor")
                .processInstanceBusinessKey(businessKey)
                .active()
                .singleResult();

        //System.out.println(" The existing ID ==="+existing.getProcessInstanceId());
        if (existing == null) {
            runtimeService.startProcessInstanceByKey(
                    "employeePayrollSupervisor",
                    businessKey
            );
        }
    }

    private String businessKey(Long employeeId) {
        return "EMPLOYEE_" + employeeId;
    }
}
