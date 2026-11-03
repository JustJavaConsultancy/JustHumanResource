package com.justjava.humanresource.finance;

import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class FinanceLockApprovalGuard {

    static final String LOCK_PROCESS_KEY = "payrollPeriodCloseProcess";
    static final String LOCK_TASK_KEY = "financeOfficer";

    private final AuthenticationManager authenticationManager;

    /** True only for the "financeOfficer" user task of the payrollPeriodCloseProcess. */
    public boolean isLockApprovalTask(Task task) {
        if (task == null) {
            return false;
        }
        String processDefinitionId = task.getProcessDefinitionId();
        return LOCK_TASK_KEY.equals(task.getTaskDefinitionKey())
                && processDefinitionId != null
                && processDefinitionId.startsWith(LOCK_PROCESS_KEY + ":");
    }


    public void assertMayDecide(Task task) {
        if (isLockApprovalTask(task) && !authenticationManager.isFinancialOfficer()) {
            throw new AccessDeniedException(
                    "Only finance officers may approve or reject the payroll period close lock");
        }
    }
}