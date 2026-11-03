package com.justjava.humanresource.finance;

import com.justjava.humanresource.core.config.AuthenticationManager;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * STAGE 1 (D4) tests for the central finance lock approval check.
 */
class FinanceLockApprovalGuardTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final FinanceLockApprovalGuard guard = new FinanceLockApprovalGuard(authenticationManager);

    private Task task(String taskDefinitionKey, String processDefinitionId) {
        Task task = mock(Task.class);
        when(task.getTaskDefinitionKey()).thenReturn(taskDefinitionKey);
        when(task.getProcessDefinitionId()).thenReturn(processDefinitionId);
        return task;
    }

    private Task lockTask() {
        return task("financeOfficer", "payrollPeriodCloseProcess:3:a1b2c3");
    }

    @Test
    void financeOfficer_mayDecideTheLock() {
        when(authenticationManager.isFinancialOfficer()).thenReturn(true);

        assertDoesNotThrow(() -> guard.assertMayDecide(lockTask()));
    }

    @Test
    void userOutsideFinanceGroup_isRefused() {
        when(authenticationManager.isFinancialOfficer()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> guard.assertMayDecide(lockTask()));
    }

    @Test
    void sameTaskNameInAnotherProcess_isNotGuarded() {
        Task other = task("financeOfficer", "someOtherProcess:1:x");

        assertFalse(guard.isLockApprovalTask(other));
        assertDoesNotThrow(() -> guard.assertMayDecide(other));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void otherTaskInThePayrollCloseProcess_isNotGuarded() {
        Task other = task("someOtherStep", "payrollPeriodCloseProcess:3:a1b2c3");

        assertFalse(guard.isLockApprovalTask(other));
        assertDoesNotThrow(() -> guard.assertMayDecide(other));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void processWhoseNameOnlyStartsTheSame_isNotGuarded() {
        Task other = task("financeOfficer", "payrollPeriodCloseProcessExtra:1:x");

        assertFalse(guard.isLockApprovalTask(other));
    }

    @Test
    void lockTaskIsRecognised() {
        assertTrue(guard.isLockApprovalTask(lockTask()));
    }

    @Test
    void nullTask_isNotGuarded() {
        assertFalse(guard.isLockApprovalTask(null));
    }
}