package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromotionApprovalDecisionService {

    private final PromotionRequestRepository requestRepository;
    private final PromotionApprovalStepRepository stepRepository;
    private final EmployeeRepository employeeRepository;
    private final PromotionActivityService activityService;
    private final PromotionNotificationService notificationService;

    @Transactional
    public void record(Long requestId, PromotionApprovalStage stage, String decision,
                       String comment, Long actorId, String taskId) {
        PromotionRequest request = request(requestId);
        PromotionApprovalStep step = stepRepository
                .findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                        requestId, PromotionApprovalDecision.PENDING)
                .orElseThrow(() -> new IllegalStateException("No pending promotion approval step."));
        if (step.getStage() != stage) {
            throw new IllegalStateException("Expected " + step.getStage() + " approval but received " + stage + ".");
        }

        PromotionApprovalDecision recordedDecision = switch (decision) {
            case "APPROVE" -> PromotionApprovalDecision.APPROVED;
            case "REJECT" -> PromotionApprovalDecision.REJECTED;
            case "RETURN" -> PromotionApprovalDecision.RETURNED;
            default -> throw new IllegalArgumentException("Unsupported promotion decision: " + decision);
        };
        Employee actor = actorId == null ? null : employeeRepository.findById(actorId).orElse(null);
        step.setDecision(recordedDecision);
        step.setComment(comment);
        step.setActedAt(LocalDateTime.now());
        step.setFlowableTaskId(taskId);
        stepRepository.save(step);
        request.setLastApprovalComment(comment);

        if (recordedDecision == PromotionApprovalDecision.APPROVED) {
            stepRepository.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                            requestId, PromotionApprovalDecision.PENDING)
                    .ifPresent(next -> {
                        request.setStatus(statusFor(next.getStage()));
                        notificationService.approvalAssigned(request, next);
                    });
            activityService.record(request, PromotionActivityType.APPROVED, actor,
                    stage + " approval completed.");
        }
        requestRepository.save(request);
    }

    @Transactional
    public void finalizeApproval(Long requestId) {
        PromotionRequest request = request(requestId);
        if (request.getStatus() == PromotionStatus.APPROVED) return;
        if (stepRepository.findFirstByPromotionRequest_IdAndDecisionOrderBySequenceNumberAsc(
                requestId, PromotionApprovalDecision.PENDING).isPresent()) {
            throw new IllegalStateException("Promotion still has pending approval steps.");
        }
        request.setStatus(PromotionStatus.APPROVED);
        request.setApprovedAt(LocalDateTime.now());
        requestRepository.save(request);
        notificationService.approved(request);
    }

    @Transactional
    public void finalizeRejection(Long requestId, String comment, Long actorId) {
        PromotionRequest request = request(requestId);
        request.setStatus(PromotionStatus.REJECTED);
        request.setRejectedAt(LocalDateTime.now());
        request.setRejectionReason(comment);
        requestRepository.save(request);
        activityService.record(request, PromotionActivityType.REJECTED, actor(actorId),
                "Promotion request rejected.");
        notificationService.rejected(request);
    }

    @Transactional
    public void returnForCorrection(Long requestId, String comment, Long actorId) {
        PromotionRequest request = request(requestId);
        request.setStatus(PromotionStatus.DRAFT);
        request.setSubmittedAt(null);
        request.setApprovedAt(null);
        requestRepository.save(request);
        activityService.record(request, PromotionActivityType.RETURNED, actor(actorId),
                "Promotion request returned for correction: " + comment);
        notificationService.returned(request);
    }

    private PromotionRequest request(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Promotion request not found: " + requestId));
    }

    private Employee actor(Long actorId) {
        return actorId == null ? null : employeeRepository.findById(actorId).orElse(null);
    }

    private PromotionStatus statusFor(PromotionApprovalStage stage) {
        return switch (stage) {
            case LINE_MANAGER -> PromotionStatus.LINE_MANAGER_REVIEW;
            case HR -> PromotionStatus.HR_REVIEW;
            case FINANCE -> PromotionStatus.FINANCE_REVIEW;
            case EXECUTIVE -> PromotionStatus.EXECUTIVE_REVIEW;
        };
    }
}
