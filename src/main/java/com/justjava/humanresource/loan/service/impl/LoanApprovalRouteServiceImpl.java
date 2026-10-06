package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.approval.model.ApprovalContext;
import com.justjava.humanresource.approval.model.ApproverRef;
import com.justjava.humanresource.approval.service.ApprovalRouteResolverFactory;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.repository.EmployeeLoanApprovalStepRepository;
import com.justjava.humanresource.loan.service.LoanApprovalRouteService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanApprovalRouteServiceImpl implements LoanApprovalRouteService {

    private final EmployeeLoanApprovalStepRepository stepRepository;
    private final ApprovalRouteResolverFactory resolverFactory;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository applicationRepository;

    @Override
    @Transactional
    public String startApproval(EmployeeLoanApplication app) {
        if (app.getStatus() != LoanApplicationStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted application can start approval.");
        }
        if (app.getWorkflowInstanceId() != null) {
            throw new IllegalStateException("This application already has an active approval workflow.");
        }
        LoanApprovalRouteType routeType = app.getApprovalRouteTypeSnapshot();
        if (routeType == null) {
            throw new IllegalStateException("Approval route was not captured at submission.");
        }

        // Resolve and persist the steps first so an empty custom route fails before any process starts.
        createSteps(app, routeType);

        Map<String, Object> variables = new HashMap<>();
        variables.put("loanApplicationId", app.getId());
        variables.put("routeType", routeType.name());
        variables.put("requesterEmployeeId", app.getEmployee().getId());

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                PROCESS_KEY, app.getApplicationNumber(), variables);
        return instance.getId();
    }

    @Override
    @Transactional
    public void cancelApproval(EmployeeLoanApplication app) {
        String instanceId = app.getWorkflowInstanceId();
        if (instanceId == null) {
            return;
        }
        if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count() > 0) {
            runtimeService.deleteProcessInstance(instanceId, "Cancelled by applicant");
        }
        app.setWorkflowInstanceId(null);
    }

    @Override
    public List<EmployeeLoanApprovalStep> getLatestAttemptSteps(Long loanApplicationId) {
        List<EmployeeLoanApprovalStep> latest = LoanApplicationSupport.latestAttempt(
                stepRepository.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(loanApplicationId));
        return latest.stream().sorted(Comparator.comparing(EmployeeLoanApprovalStep::getSequenceNo)).toList();
    }

    @Override
    public Optional<EmployeeLoanApprovalStep> getCurrentStep(Long loanApplicationId) {
        return LoanApplicationSupport.currentPending(
                stepRepository.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(loanApplicationId));
    }

    @Override
    public Optional<EmployeeLoanApprovalStep> getNextStep(Long loanApplicationId) {
        List<EmployeeLoanApprovalStep> pending = getLatestAttemptSteps(loanApplicationId).stream()
                .filter(s -> s.getDecision() == null).toList();
        return pending.size() > 1 ? Optional.of(pending.get(1)) : Optional.empty();
    }

    @Override
    public Optional<String> findActiveTaskId(Long loanApplicationId) {
        return applicationRepository.findById(loanApplicationId)
                .map(EmployeeLoanApplication::getWorkflowInstanceId)
                .flatMap(instanceId -> taskService.createTaskQuery()
                        .processInstanceId(instanceId).active().list().stream()
                        .map(Task::getId).findFirst());
    }

    // ------------------------------------------------------------------ steps

    private void createSteps(EmployeeLoanApplication app, LoanApprovalRouteType routeType) {
        if (routeType == LoanApprovalRouteType.ROLE_BASED) {
            stepRepository.save(newStep(app, 1, LoanApprovalStage.HR, null, HR_GROUP));
            stepRepository.save(newStep(app, 2, LoanApprovalStage.FINANCE, null, FINANCE_GROUP));
            return;
        }

        ApprovalContext context = ApprovalContext.builder()
                .moduleType(ApprovalModuleType.LOAN)
                .routeType(ApprovalRouteType.CUSTOM)
                .requesterEmployeeId(app.getEmployee().getId())
                .moduleRefId(app.getId())
                .customApprovalPathId(app.getCustomApprovalPathIdSnapshot())
                .build();
        // The resolver validates the path, drops duplicates and excludes the requester.
        List<ApproverRef> approvers = new ArrayList<>(resolverFactory.getResolver(context).resolveApprovers(context));
        if (approvers.isEmpty()) {
            throw new IllegalStateException("The custom approval path resolves to no approvers for this employee. "
                    + "Ask HR to review the path before submitting.");
        }
        approvers.sort(Comparator.comparingInt(ApproverRef::getLevel));

        int sequence = 1;
        for (ApproverRef approver : approvers) {
            stepRepository.save(newStep(app, sequence++, LoanApprovalStage.CUSTOM, approver.getEmployeeId(), null));
        }
    }

    private EmployeeLoanApprovalStep newStep(EmployeeLoanApplication app, int sequenceNo, LoanApprovalStage stage,
                                             Long approverEmployeeId, String group) {
        EmployeeLoanApprovalStep step = new EmployeeLoanApprovalStep();
        step.setLoanApplicationId(app.getId());
        step.setSequenceNo(sequenceNo);
        step.setApprovalStage(stage);
        step.setApproverEmployeeId(approverEmployeeId);
        step.setApproverGroup(group);
        return step;
    }
}