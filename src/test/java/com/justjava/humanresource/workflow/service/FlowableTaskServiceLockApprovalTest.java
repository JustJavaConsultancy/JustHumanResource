package com.justjava.humanresource.workflow.service;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.finance.FinanceLockApprovalGuard;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.flowable.task.api.TaskQuery;
import static org.mockito.Mockito.doReturn;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STAGE 1 (D4) tests: completeTask is the single point every entry point goes through
 * (web and mobile finance controllers, and any other caller that completes a task by id).
 * A refused caller must not change anything: no variables set, task not completed.
 */
class FlowableTaskServiceLockApprovalTest {

    private TaskService taskService;
    private RuntimeService runtimeService;
    private AuthenticationManager authenticationManager;
    private FlowableTaskService service;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        runtimeService = mock(RuntimeService.class);
        authenticationManager = mock(AuthenticationManager.class);

        service = new FlowableTaskService(taskService, runtimeService);
        ReflectionTestUtils.setField(service, "financeLockApprovalGuard",
                new FinanceLockApprovalGuard(authenticationManager));
    }

    private Task givenTask(String taskDefinitionKey, String processDefinitionId) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getProcessInstanceId()).thenReturn("pi1");
        when(task.getTaskDefinitionKey()).thenReturn(taskDefinitionKey);
        when(task.getProcessDefinitionId()).thenReturn(processDefinitionId);
        TaskQuery query = mock(TaskQuery.class, Mockito.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        doReturn(task).when(query).singleResult();
        return task;
    }

    private void givenLockTask() {
        givenTask("financeOfficer", "payrollPeriodCloseProcess:3:a1b2c3");
    }

    @Test
    void nonFinanceUser_cannotApprove_andTaskStaysActive() {
        givenLockTask();
        when(authenticationManager.isFinancialOfficer()).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.completeTask("t1", Map.of("approved", true)));

        verify(runtimeService, never()).setVariables(any(), any());
        verify(taskService, never()).complete(anyString());
    }

    @Test
    void nonFinanceUser_cannotReject_andTaskStaysActive() {
        givenLockTask();
        when(authenticationManager.isFinancialOfficer()).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.completeTask("t1", Map.of("approved", false)));

        verify(runtimeService, never()).setVariables(any(), any());
        verify(taskService, never()).complete(anyString());
    }

    @Test
    void financeOfficer_canApprove() {
        givenLockTask();
        when(authenticationManager.isFinancialOfficer()).thenReturn(true);

        service.completeTask("t1", Map.of("approved", true));

        verify(runtimeService).setVariables(eq("pi1"), eq(Map.of("approved", true)));
        verify(taskService).complete("t1");
    }

    @Test
    void financeOfficer_canReject() {
        givenLockTask();
        when(authenticationManager.isFinancialOfficer()).thenReturn(true);

        service.completeTask("t1", Map.of("approved", false));

        verify(runtimeService).setVariables(eq("pi1"), eq(Map.of("approved", false)));
        verify(taskService).complete("t1");
    }

    @Test
    void otherTasks_areNotAffected_forUsersOutsideTheFinanceGroup() {
        givenTask("managerApproval", "leaveProcess:2:z9");
        when(authenticationManager.isFinancialOfficer()).thenReturn(false);

        service.completeTask("t1", Map.of("approved", true));

        verify(taskService).complete("t1");
    }
}