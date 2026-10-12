package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.dto.PromotionDecisionCommand;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionWorkflowServiceImpl implements PromotionWorkflowService {

    public static final String PROCESS_KEY = "promotionApprovalProcess";
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final FlowableTaskService flowableTaskService;
    private final PromotionCurrentUserService currentUserService;
    private final PromotionAuthorizationService authorizationService;

    @Override
    @Transactional
    public String startApproval(PromotionRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("promotionRequestId", request.getId());
        variables.put("requestNumber", request.getRequestNumber());
        variables.put("employeeId", request.getEmployee().getId());
        variables.put("employeeName", request.getEmployee().getFullName());
        variables.put("salaryIncreaseAmount", request.getSalaryIncreaseAmount());
        variables.put("salaryIncreasePercent", request.getSalaryIncreasePercent());
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                PROCESS_KEY, "PROMOTION_" + request.getId(), variables);
        return instance.getProcessInstanceId();
    }

    @Override
    public List<FlowableTaskDTO> getMyTasks() {
        Map<String, FlowableTaskDTO> tasks = new LinkedHashMap<>();
        currentUserService.findCurrentEmployee().map(Employee::getId).map(String::valueOf)
                .ifPresent(id -> flowableTaskService.getTasksForAssignee(id, PROCESS_KEY)
                        .forEach(task -> tasks.put(task.getTaskId(), task)));

        List<String> groups = authorizationService.candidateGroupsForCurrentUser();
        if (!groups.isEmpty()) {
            flowableTaskService.getTasksForCandidateGroups(groups, PROCESS_KEY)
                    .forEach(task -> tasks.put(task.getTaskId(), task));
        }
        return List.copyOf(tasks.values());
    }

    @Override
    @Transactional
    public void approve(String taskId, PromotionDecisionCommand command) {
        complete(taskId, "APPROVE", comment(command), false);
    }

    @Override
    @Transactional
    public void reject(String taskId, PromotionDecisionCommand command) {
        complete(taskId, "REJECT", comment(command), true);
    }

    @Override
    @Transactional
    public void returnForCorrection(String taskId, PromotionDecisionCommand command) {
        complete(taskId, "RETURN", comment(command), true);
    }

    @Override
    @Transactional
    public void cancelApproval(PromotionRequest request, String reason) {
        String processInstanceId = request.getProcessInstanceId();
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance != null) {
            runtimeService.deleteProcessInstance(processInstanceId,
                    reason == null || reason.isBlank() ? "Promotion request cancelled." : reason);
        }
    }

    @Override
    public String activeTaskId(PromotionRequest request) {
        if (request.getProcessInstanceId() == null || request.getProcessInstanceId().isBlank()) {
            throw new IllegalStateException("Promotion request has no active approval process.");
        }
        Task task = taskService.createTaskQuery()
                .processInstanceId(request.getProcessInstanceId())
                .active()
                .singleResult();
        if (task == null) throw new IllegalStateException("Promotion request has no active approval task.");
        return task.getId();
    }

    @Override
    @Transactional
    public void assignActiveTask(PromotionRequest request, Long approverId) {
        Task task = activeTask(request);
        taskService.setAssignee(task.getId(), String.valueOf(approverId));
    }

    @Override
    @Transactional
    public void assignActiveTaskToGroup(PromotionRequest request, String approverGroup) {
        if (approverGroup == null || approverGroup.isBlank()) {
            throw new IllegalArgumentException("Escalation approver group is required.");
        }
        Task task = activeTask(request);
        taskService.setAssignee(task.getId(), null);
        taskService.addCandidateGroup(task.getId(), approverGroup.trim());
    }

    private void complete(String taskId, String decision, String comment, boolean commentRequired) {
        if (commentRequired && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException(decision.equals("REJECT")
                    ? "A rejection comment is required." : "A return comment is required.");
        }
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null || !task.getProcessDefinitionId().startsWith(PROCESS_KEY + ":")) {
            throw new IllegalStateException("Active promotion approval task not found: " + taskId);
        }
        Employee actor = currentUserService.findCurrentEmployee()
                .orElseThrow(() -> new AccessDeniedException("A linked employee account is required."));
        PromotionApprovalStage stage = stageForTask(task.getTaskDefinitionKey());
        Object promotionRequestId = runtimeService.getVariable(task.getProcessInstanceId(), "promotionRequestId");
        Long requestId = promotionRequestId == null ? null : number(promotionRequestId);
        authorizationService.requireCanActOnTask(task, actor, stage, requestId);

        Object employeeId = runtimeService.getVariable(task.getProcessInstanceId(), "employeeId");
        authorizationService.requireNotSelfApproval(actor, employeeId);

        Map<String, Object> variables = new HashMap<>();
        variables.put("approvalDecision", decision);
        variables.put("approvalComment", comment);
        variables.put("approvalActorId", actor.getId());
        variables.put("flowableTaskId", taskId);
        variables.put("approvalStage", stage.name());
        flowableTaskService.completeTask(taskId, variables);
    }

    private Task activeTask(PromotionRequest request) {
        if (request.getProcessInstanceId() == null || request.getProcessInstanceId().isBlank()) {
            throw new IllegalStateException("Promotion request has no active approval process.");
        }
        Task task = taskService.createTaskQuery()
                .processInstanceId(request.getProcessInstanceId())
                .active()
                .singleResult();
        if (task == null) throw new IllegalStateException("Promotion request has no active approval task.");
        return task;
    }

    private PromotionApprovalStage stageForTask(String taskDefinitionKey) {
        return switch (taskDefinitionKey) {
            case "lineManagerApproval" -> PromotionApprovalStage.LINE_MANAGER;
            case "hrApproval" -> PromotionApprovalStage.HR;
            case "financeApproval" -> PromotionApprovalStage.FINANCE;
            case "executiveApproval" -> PromotionApprovalStage.EXECUTIVE;
            default -> throw new IllegalStateException("Unknown promotion task: " + taskDefinitionKey);
        };
    }

    private String comment(PromotionDecisionCommand command) {
        return command == null || command.getComment() == null || command.getComment().isBlank()
                ? null : command.getComment().trim();
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }
}
