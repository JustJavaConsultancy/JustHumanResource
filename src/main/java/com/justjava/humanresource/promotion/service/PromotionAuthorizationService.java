package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionAuthorizationService {

    public static final String HR_GROUP = "humanresource";
    public static final String FINANCE_GROUP = "financialofficers";
    public static final String EXECUTIVE_GROUP = "executive";

    private final AuthenticationManager authenticationManager;
    private final PromotionCurrentUserService currentUserService;
    private final PromotionApprovalStepRepository approvalStepRepository;

    public void requireHrOrAdmin() {
        if (!(authenticationManager.isHumanResource() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("HR access is required.");
        }
    }

    public void requireHrFinanceOrAdmin() {
        if (!(authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer()
                || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("HR or Finance access is required.");
        }
    }

    public boolean canViewSalary() {
        return authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer()
                || authenticationManager.isExecutive()
                || authenticationManager.isAdmin();
    }

    public void requireCanView(PromotionRequest request) {
        if (authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer()
                || authenticationManager.isExecutive()
                || authenticationManager.isAdmin()) {
            return;
        }
        Employee current = currentUserService.getCurrentEmployeeOrSystem();
        if (isCurrentEmployee(current, request.getEmployee()) || isCurrentEmployee(current, request.getRequestedBy())) {
            return;
        }
        if (current != null && approvalStepRepository
                .findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                        request.getId(), PromotionApprovalDecision.PENDING)
                .filter(step -> step.getApprover() != null)
                .map(PromotionApprovalStep::getApprover)
                .map(Employee::getId)
                .filter(current.getId()::equals)
                .isPresent()) {
            return;
        }
        throw new AccessDeniedException("You cannot view this promotion request.");
    }

    private boolean isCurrentEmployee(Employee current, Employee expected) {
        return current != null && expected != null && current.getId() != null && current.getId().equals(expected.getId());
    }

    public void requireCanActOnTask(Task task, Employee actor, PromotionApprovalStage stage) {
        requireCanActOnTask(task, actor, stage, null);
    }

    public void requireCanActOnTask(Task task, Employee actor, PromotionApprovalStage stage, Long requestId) {
        if (requestId != null) {
            approvalStepRepository.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                            requestId, PromotionApprovalDecision.PENDING)
                    .map(PromotionApprovalStep::getApprover)
                    .filter(approver -> approver.getId() != null)
                    .ifPresent(approver -> {
                        if (!approver.getId().equals(actor.getId())) {
                            throw new AccessDeniedException("This promotion approval is assigned to another approver.");
                        }
                    });
        }
        if (stage == PromotionApprovalStage.LINE_MANAGER) {
            if (!actor.getId().toString().equals(task.getAssignee())) {
                throw new AccessDeniedException("This promotion approval is not assigned to you.");
            }
            return;
        }
        boolean allowed = switch (stage) {
            case HR -> authenticationManager.isAdmin() || authenticationManager.isHumanResource();
            case FINANCE -> authenticationManager.isAdmin() || authenticationManager.isFinancialOfficer();
            case EXECUTIVE -> authenticationManager.isAdmin() || authenticationManager.isExecutive();
            default -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("You cannot act on this promotion approval.");
        }
    }

    public void requireNotSelfApproval(Employee actor, Object promotedEmployeeId) {
        if (promotedEmployeeId != null && actor.getId().equals(Long.valueOf(String.valueOf(promotedEmployeeId)))) {
            throw new AccessDeniedException("You cannot approve your own promotion request.");
        }
    }

    public List<String> candidateGroupsForCurrentUser() {
        List<String> groups = new ArrayList<>();
        if (authenticationManager.isHumanResource() || authenticationManager.isAdmin()) groups.add(HR_GROUP);
        if (authenticationManager.isFinancialOfficer() || authenticationManager.isAdmin()) groups.add(FINANCE_GROUP);
        if (authenticationManager.isExecutive() || authenticationManager.isAdmin()) groups.add(EXECUTIVE_GROUP);
        return groups;
    }
}
