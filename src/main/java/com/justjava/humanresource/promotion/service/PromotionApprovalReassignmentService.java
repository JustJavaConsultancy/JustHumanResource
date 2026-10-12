package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.dto.PromotionDetailResponse;
import com.justjava.humanresource.promotion.dto.PromotionReassignmentCommand;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromotionApprovalReassignmentService {

    private final PromotionRequestRepository requestRepository;
    private final PromotionApprovalStepRepository stepRepository;
    private final EmployeeRepository employeeRepository;
    private final PromotionAuthorizationService authorizationService;
    private final PromotionCurrentUserService currentUserService;
    private final PromotionWorkflowService workflowService;
    private final PromotionActivityService activityService;
    private final PromotionNotificationService notificationService;
    private final PromotionMapper mapper;

    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse reassign(Long requestId, Long stepId, PromotionReassignmentCommand command) {
        return changeAssignee(requestId, stepId, command, false);
    }

    @Transactional
    @PreAuthorize("@promotionSecurity.canManagePromotions()")
    public PromotionDetailResponse delegate(Long requestId, Long stepId, PromotionReassignmentCommand command) {
        return changeAssignee(requestId, stepId, command, true);
    }

    private PromotionDetailResponse changeAssignee(
            Long requestId,
            Long stepId,
            PromotionReassignmentCommand command,
            boolean delegation) {
        authorizationService.requireHrOrAdmin();
        PromotionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Promotion request not found: " + requestId));
        PromotionApprovalStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("Promotion approval step not found: " + stepId));
        if (step.getPromotionRequest() == null || !requestId.equals(step.getPromotionRequest().getId())) {
            throw new IllegalArgumentException("Approval step does not belong to this promotion request.");
        }
        if (step.getDecision() != PromotionApprovalDecision.PENDING) {
            throw new IllegalStateException("Only pending approval steps can be reassigned or delegated.");
        }

        PromotionApprovalStep activeStep = stepRepository
                .findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                        requestId, PromotionApprovalDecision.PENDING)
                .orElseThrow(() -> new IllegalStateException("No pending approval step for this promotion request."));
        if (!step.getId().equals(activeStep.getId())) {
            throw new IllegalStateException("Only the active approval step can be reassigned or delegated.");
        }

        Employee newApprover = employeeRepository.findById(command.getApproverId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + command.getApproverId()));
        Employee actor = currentUserService.getCurrentEmployeeOrSystem();
        Employee previousApprover = step.getApprover();
        String previousGroup = step.getApproverGroup();
        if (step.getOriginalApprover() == null) {
            step.setOriginalApprover(previousApprover);
        }

        LocalDateTime now = LocalDateTime.now();
        step.setApprover(newApprover);
        step.setAssignedAt(now);
        step.setApproverGroup(null);
        if (delegation) {
            step.setDelegatedBy(actor);
            step.setDelegatedAt(now);
            step.setDelegationComment(comment(command));
        } else {
            step.setReassignedBy(actor);
            step.setReassignedAt(now);
            step.setReassignmentComment(comment(command));
        }
        stepRepository.save(step);

        workflowService.assignActiveTask(request, newApprover.getId());
        activityService.record(request,
                delegation ? PromotionActivityType.APPROVAL_DELEGATED : PromotionActivityType.APPROVAL_REASSIGNED,
                actor,
                description(delegation, step, previousApprover, previousGroup, newApprover, command));
        notificationService.approvalAssigned(request, step);
        return mapper.toDetail(request);
    }

    private String description(boolean delegation, PromotionApprovalStep step, Employee previousApprover, String previousGroup,
                               Employee newApprover, PromotionReassignmentCommand command) {
        String action = delegation ? "delegated" : "reassigned";
        String previous = previousApprover == null ? previousGroup : previousApprover.getFullName();
        String description = "Promotion " + step.getStage() + " approval " + action
                + " from " + (previous == null ? "candidate group" : previous)
                + " to " + newApprover.getFullName() + ".";
        String comment = comment(command);
        return comment == null ? description : description + " " + comment;
    }

    private String comment(PromotionReassignmentCommand command) {
        return command == null || command.getComment() == null || command.getComment().isBlank()
                ? null : command.getComment().trim();
    }
}
