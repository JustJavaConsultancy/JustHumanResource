package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.dto.PromotionDecisionCommand;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionWorkflowServiceImpl;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionWorkflowServiceAuthorizationTest {

    @Mock RuntimeService runtimeService;
    @Mock TaskService taskService;
    @Mock FlowableTaskService flowableTaskService;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionAuthorizationService authorizationService;

    private PromotionWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromotionWorkflowServiceImpl(
                runtimeService,
                taskService,
                flowableTaskService,
                currentUserService,
                authorizationService
        );
    }

    @Test
    void rejectsUnauthorizedTaskCompletionBeforeCompletingFlowableTask() {
        Task task = activeTask("hrApproval", false);
        Employee actor = new Employee();
        actor.setId(44L);
        when(currentUserService.findCurrentEmployee()).thenReturn(Optional.of(actor));
        doThrow(new AccessDeniedException("You cannot act on this promotion approval."))
                .when(authorizationService).requireCanActOnTask(task, actor, PromotionApprovalStage.HR, null);

        PromotionDecisionCommand command = new PromotionDecisionCommand();
        command.setComment("Approved");

        assertThatThrownBy(() -> service.approve("task-1", command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You cannot act on this promotion approval.");

        verify(flowableTaskService, never()).completeTask(org.mockito.Mockito.eq("task-1"), anyMap());
    }

    @Test
    void rejectsSelfApprovalBeforeCompletingFlowableTask() {
        Task task = activeTask("hrApproval", true);
        Employee actor = new Employee();
        actor.setId(44L);
        when(currentUserService.findCurrentEmployee()).thenReturn(Optional.of(actor));
        when(runtimeService.getVariable("process-1", "promotionRequestId")).thenReturn(15L);
        when(runtimeService.getVariable("process-1", "employeeId")).thenReturn(44L);
        doThrow(new AccessDeniedException("You cannot approve your own promotion request."))
                .when(authorizationService).requireNotSelfApproval(actor, 44L);

        PromotionDecisionCommand command = new PromotionDecisionCommand();
        command.setComment("Approved");

        assertThatThrownBy(() -> service.approve("task-1", command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You cannot approve your own promotion request.");

        verify(flowableTaskService, never()).completeTask(org.mockito.Mockito.eq("task-1"), anyMap());
    }

    private Task activeTask(String taskDefinitionKey, boolean includeProcessInstanceId) {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.active()).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(task.getProcessDefinitionId()).thenReturn(PromotionWorkflowServiceImpl.PROCESS_KEY + ":1:definition");
        when(task.getTaskDefinitionKey()).thenReturn(taskDefinitionKey);
        if (includeProcessInstanceId) {
            when(task.getProcessInstanceId()).thenReturn("process-1");
        }
        return task;
    }
}
