package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.loan.dto.LoanApprovalTaskResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.service.LoanApprovalRouteService;
import com.justjava.humanresource.loan.service.LoanApprovalService;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanApprovalServiceImpl implements LoanApprovalService {

    private static final String HR_TASK_KEY = "hrTask";
    private static final String FINANCE_TASK_KEY = "financeTask";
    private static final String CUSTOM_TASK_KEY = "customTask";
    private static final int MAX_COMMENT = 2000;

    private final FlowableTaskService flowableTaskService;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final EmployeeLoanApplicationRepository applicationRepository;
    private final LoanApprovalRouteService routeService;
    private final LoanEmployeeContextService contextService;
    private final AuthenticationManager authenticationManager;

    // =====================================================================
    // Queues
    // =====================================================================

    @Override
    public List<LoanApprovalTaskResponse> listHrTasks() {
        requireHr();
        Long me = contextService.getCurrentEmployee().getId();
        return toResponses(flowableTaskService.getTasksForCandidateGroup(
                LoanApprovalRouteService.HR_GROUP, LoanApprovalRouteService.PROCESS_KEY),
                HR_TASK_KEY, LoanApprovalStage.HR, me);
    }

    @Override
    public List<LoanApprovalTaskResponse> listFinanceTasks() {
        requireFinance();
        Long me = contextService.getCurrentEmployee().getId();
        return toResponses(flowableTaskService.getTasksForCandidateGroup(
                LoanApprovalRouteService.FINANCE_GROUP, LoanApprovalRouteService.PROCESS_KEY),
                FINANCE_TASK_KEY, LoanApprovalStage.FINANCE, me);
    }

    @Override
    public List<LoanApprovalTaskResponse> listMyCustomTasks() {
        Long me = contextService.getCurrentEmployee().getId();
        return toResponses(flowableTaskService.getTasksForAssignee(
                String.valueOf(me), LoanApprovalRouteService.PROCESS_KEY),
                CUSTOM_TASK_KEY, LoanApprovalStage.CUSTOM, null);
    }

    // =====================================================================
    // Decisions
    // =====================================================================

    @Override @Transactional
    public void approveHr(String taskId, String comment) { complete(LoanApprovalStage.HR, taskId, "APPROVE", comment); }

    @Override @Transactional
    public void rejectHr(String taskId, String comment) { complete(LoanApprovalStage.HR, taskId, "REJECT", comment); }

    @Override @Transactional
    public void returnHr(String taskId, String comment) { complete(LoanApprovalStage.HR, taskId, "RETURN", comment); }

    @Override @Transactional
    public void approveFinance(String taskId, String comment) { complete(LoanApprovalStage.FINANCE, taskId, "APPROVE", comment); }

    @Override @Transactional
    public void rejectFinance(String taskId, String comment) { complete(LoanApprovalStage.FINANCE, taskId, "REJECT", comment); }

    @Override @Transactional
    public void returnFinance(String taskId, String comment) { complete(LoanApprovalStage.FINANCE, taskId, "RETURN", comment); }

    @Override @Transactional
    public void approveCustom(String taskId, String comment) { complete(LoanApprovalStage.CUSTOM, taskId, "APPROVE", comment); }

    @Override @Transactional
    public void rejectCustom(String taskId, String comment) { complete(LoanApprovalStage.CUSTOM, taskId, "REJECT", comment); }

    @Override @Transactional
    public void returnCustom(String taskId, String comment) { complete(LoanApprovalStage.CUSTOM, taskId, "RETURN", comment); }

    private void complete(LoanApprovalStage stage, String taskId, String decision, String comment) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task id is required.");
        }
        String cleanComment = comment == null || comment.isBlank() ? null : comment.trim();
        if (!"APPROVE".equals(decision) && cleanComment == null) {
            throw new IllegalArgumentException(
                    ("REJECT".equals(decision) ? "A rejection" : "A return") + " comment is required.");
        }
        if (cleanComment != null && cleanComment.length() > MAX_COMMENT) {
            throw new IllegalArgumentException("Comment must not exceed " + MAX_COMMENT + " characters.");
        }

        // Role check first, so a non-approver learns nothing about the task.
        switch (stage) {
            case HR -> requireHr();
            case FINANCE -> requireFinance();
            case CUSTOM -> { /* assignment is checked against the task below */ }
        }
        Employee actor = contextService.getCurrentEmployee();

        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .processDefinitionKey(LoanApprovalRouteService.PROCESS_KEY)
                .singleResult();
        if (task == null) {
            throw new IllegalStateException("This approval task is no longer available.");
        }
        if (!expectedTaskKey(stage).equals(task.getTaskDefinitionKey())) {
            throw new IllegalStateException("This task is not a " + stage.name().toLowerCase(Locale.ROOT)
                    + " approval task.");
        }

        switch (stage) {
            case HR -> requireCandidateGroup(taskId, LoanApprovalRouteService.HR_GROUP);
            case FINANCE -> requireCandidateGroup(taskId, LoanApprovalRouteService.FINANCE_GROUP);
            case CUSTOM -> {
                if (!flowableTaskService.isTaskAssignedTo(taskId, String.valueOf(actor.getId()))) {
                    throw new AccessDeniedException("This task is not assigned to you.");
                }
            }
        }

        Object rawId = runtimeService.getVariable(task.getProcessInstanceId(), "loanApplicationId");
        if (rawId == null) {
            throw new IllegalStateException("Task is not linked to a loan application.");
        }
        EmployeeLoanApplication app = applicationRepository.findById(((Number) rawId).longValue())
                .orElseThrow(() -> new EntityNotFoundException("Loan application not found: " + rawId));

        if (app.getEmployee().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You cannot decide your own loan application.");
        }
        if (app.getStatus() != pendingStatus(stage)) {
            throw new IllegalStateException("This application is not awaiting " + stage.name().toLowerCase(Locale.ROOT)
                    + " approval (status " + app.getStatus() + ").");
        }
        EmployeeLoanApprovalStep current = routeService.getCurrentStep(app.getId())
                .orElseThrow(() -> new IllegalStateException("No pending approval step for this application."));
        if (current.getApprovalStage() != stage) {
            throw new IllegalStateException("The pending approval step is " + current.getApprovalStage() + ".");
        }
        if (stage == LoanApprovalStage.CUSTOM && !actor.getId().equals(current.getApproverEmployeeId())) {
            throw new AccessDeniedException("You are not the current approver for this application.");
        }

        // Same variable contract as the request approval flow; the stage delegates record the decision.
        Map<String, Object> variables = new HashMap<>();
        variables.put("approvalDecision", decision);
        variables.put("approvalComment", cleanComment);
        variables.put("approvalActorId", actor.getId());
        variables.put("flowableTaskId", taskId);
        flowableTaskService.completeTask(taskId, variables);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private List<LoanApprovalTaskResponse> toResponses(List<FlowableTaskDTO> tasks, String taskKey,
                                                       LoanApprovalStage stage, Long excludeOwnEmployeeId) {
        List<FlowableTaskDTO> relevant = tasks.stream()
                .filter(t -> taskKey.equals(t.getTaskDefinitionKey()))
                .filter(t -> applicationIdOf(t) != null)
                .toList();
        Map<Long, EmployeeLoanApplication> apps = applicationRepository
                .findAllById(relevant.stream().map(this::applicationIdOf).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(EmployeeLoanApplication::getId, a -> a));

        List<LoanApprovalTaskResponse> result = new ArrayList<>();
        for (FlowableTaskDTO t : relevant) {
            EmployeeLoanApplication a = apps.get(applicationIdOf(t));
            if (a == null) continue;
            // People cannot decide their own loan, so it is not offered to them.
            if (excludeOwnEmployeeId != null && a.getEmployee().getId().equals(excludeOwnEmployeeId)) continue;
            result.add(LoanApprovalTaskResponse.builder()
                    .taskId(t.getTaskId())
                    .taskName(t.getTaskName())
                    .stage(stage)
                    .taskCreatedAt(t.getCreatedTime())
                    .loanApplicationId(a.getId())
                    .applicationNumber(a.getApplicationNumber())
                    .applicationStatus(a.getStatus())
                    .employeeId(a.getEmployee().getId())
                    .employeeName(a.getEmployee().getFullName())
                    .departmentName(a.getEmployee().getDepartment() == null ? null
                            : a.getEmployee().getDepartment().getName())
                    .loanProductName(a.getLoanProduct().getName())
                    .requestedAmount(a.getRequestedAmount())
                    .repaymentAmount(a.getRepaymentAmount())
                    .tenorMonths(a.getTenorMonths())
                    .repaymentStartMonth(a.getRepaymentStartMonth())
                    .submittedAt(a.getSubmittedAt())
                    .build());
        }
        return result;
    }

    private Long applicationIdOf(FlowableTaskDTO task) {
        Object raw = task.getVariables() == null ? null : task.getVariables().get("loanApplicationId");
        return raw instanceof Number n ? n.longValue() : null;
    }

    private void requireHr() {
        if (!(authenticationManager.isLoanHrApprover() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("HR access is required.");
        }
    }

    private void requireFinance() {
        if (!(authenticationManager.isLoanFinanceApprover() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("Finance access is required.");
        }
    }

    private void requireCandidateGroup(String taskId, String group) {
        if (!flowableTaskService.isTaskCandidateForGroup(taskId, group)) {
            throw new AccessDeniedException("You are not eligible to act on this task.");
        }
    }

    private static String expectedTaskKey(LoanApprovalStage stage) {
        return switch (stage) {
            case HR -> HR_TASK_KEY;
            case FINANCE -> FINANCE_TASK_KEY;
            case CUSTOM -> CUSTOM_TASK_KEY;
        };
    }

    private static LoanApplicationStatus pendingStatus(LoanApprovalStage stage) {
        return switch (stage) {
            case HR -> LoanApplicationStatus.PENDING_HR_APPROVAL;
            case FINANCE -> LoanApplicationStatus.PENDING_FINANCE_APPROVAL;
            case CUSTOM -> LoanApplicationStatus.PENDING_CUSTOM_APPROVAL;
        };
    }
}
