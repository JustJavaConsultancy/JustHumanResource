package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.promotion.dto.*;
import com.justjava.humanresource.promotion.entity.PromotionActivityLog;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionAttachment;
import com.justjava.humanresource.promotion.entity.PromotionEligibilityCheck;
import com.justjava.humanresource.promotion.entity.PromotionPayrollHandoff;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.repository.PromotionActivityLogRepository;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionAttachmentRepository;
import com.justjava.humanresource.promotion.repository.PromotionEligibilityCheckRepository;
import com.justjava.humanresource.promotion.repository.PromotionPayrollHandoffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PromotionMapper {

    private final PromotionEligibilityCheckRepository eligibilityCheckRepository;
    private final PromotionApprovalStepRepository approvalStepRepository;
    private final PromotionAttachmentRepository attachmentRepository;
    private final PromotionActivityLogRepository activityLogRepository;
    private final PromotionPayrollHandoffRepository payrollHandoffRepository;
    private final PromotionAuthorizationService authorizationService;
    private final PromotionRequiredDocumentService requiredDocumentService;

    public PromotionSummaryResponse toSummary(PromotionRequest request) {
        Employee employee = request.getEmployee();
        boolean canViewSalary = authorizationService.canViewSalary();
        return PromotionSummaryResponse.builder()
                .id(request.getId())
                .requestNumber(request.getRequestNumber())
                .employeeId(employee == null ? null : employee.getId())
                .employeeName(employee == null ? null : employee.getFullName())
                .departmentName(nameOf(request.getProposedDepartment() != null
                        ? request.getProposedDepartment() : request.getCurrentDepartment()))
                .currentRoleName(nameOf(request.getCurrentRole()))
                .proposedRoleName(nameOf(request.getProposedRole()))
                .appraisalScore(request.getAppraisalScore())
                .status(request.getStatus())
                .effectiveDate(request.getEffectiveDate())
                .requestedAt(request.getRequestedAt())
                .salaryIncreaseAmount(canViewSalary ? request.getSalaryIncreaseAmount() : null)
                .salaryIncreasePercent(canViewSalary ? request.getSalaryIncreasePercent() : null)
                .build();
    }

    public PromotionDetailResponse toDetail(PromotionRequest request) {
        Employee employee = request.getEmployee();
        boolean canViewSalary = authorizationService.canViewSalary();
        List<PromotionEligibilityCheck> checks =
                eligibilityCheckRepository.findByPromotionRequest_IdOrderByIdAsc(request.getId());
        List<PromotionApprovalStep> steps =
                approvalStepRepository.findByPromotionRequest_IdOrderBySequenceNumberAsc(request.getId());
        List<PromotionAttachment> attachments =
                attachmentRepository.findByPromotionRequest_IdOrderByUploadedAtDesc(request.getId());
        List<PromotionActivityLog> activities =
                activityLogRepository.findByPromotionRequest_IdOrderByCreatedAtDesc(request.getId());

        return PromotionDetailResponse.builder()
                .id(request.getId())
                .requestNumber(request.getRequestNumber())
                .employeeId(employee == null ? null : employee.getId())
                .employeeName(employee == null ? null : employee.getFullName())
                .employeeNumber(employee == null ? null : employee.getEmployeeNumber())
                .employeeEmail(employee == null ? null : employee.getEmail())
                .requestSource(request.getRequestSource())
                .currentDepartmentName(nameOf(request.getCurrentDepartment()))
                .proposedDepartmentName(nameOf(request.getProposedDepartment()))
                .currentRoleName(nameOf(request.getCurrentRole()))
                .proposedRoleName(nameOf(request.getProposedRole()))
                .currentSalary(canViewSalary ? request.getCurrentSalary() : null)
                .proposedSalary(canViewSalary ? request.getProposedSalary() : null)
                .salaryIncreaseAmount(canViewSalary ? request.getSalaryIncreaseAmount() : null)
                .salaryIncreasePercent(canViewSalary ? request.getSalaryIncreasePercent() : null)
                .monthlyPayrollImpact(canViewSalary ? request.getMonthlyPayrollImpact() : null)
                .annualPayrollImpact(canViewSalary ? request.getAnnualPayrollImpact() : null)
                .appraisalScore(request.getAppraisalScore())
                .justification(request.getJustification())
                .status(request.getStatus())
                .effectiveDate(request.getEffectiveDate())
                .submittedAt(request.getSubmittedAt())
                .approvedAt(request.getApprovedAt())
                .rejectedAt(request.getRejectedAt())
                .implementedAt(request.getImplementedAt())
                .rejectionReason(request.getRejectionReason())
                .implementationComment(request.getImplementationComment())
                .eligibilityChecks(checks.stream().map(this::toEligibilityResponse).toList())
                .approvalSteps(steps.stream().map(this::toApprovalResponse).toList())
                .attachments(attachments.stream().map(this::toAttachmentResponse).toList())
                .missingRequiredDocumentsBeforeSubmit(requiredDocumentService.missingRequiredDocuments(
                        request, PromotionRequiredDocumentService.EnforcementStage.SUBMIT))
                .missingRequiredDocumentsBeforeImplementation(requiredDocumentService.missingRequiredDocuments(
                        request, PromotionRequiredDocumentService.EnforcementStage.IMPLEMENTATION))
                .payrollHandoff(canViewSalary ? payrollHandoffRepository.findByPromotionRequest_Id(request.getId())
                        .map(this::toPayrollHandoffResponse)
                        .orElse(null) : null)
                .activities(activities.stream().map(this::toActivityResponse).toList())
                .build();
    }

    private PromotionEligibilityCheckResponse toEligibilityResponse(PromotionEligibilityCheck check) {
        Employee overriddenBy = check.getOverriddenBy();
        return PromotionEligibilityCheckResponse.builder()
                .id(check.getId())
                .checkCode(check.getCheckCode())
                .checkName(check.getCheckName())
                .passed(check.isPassed())
                .severity(check.getSeverity())
                .message(check.getMessage())
                .overrideAllowed(check.isOverrideAllowed())
                .overridden(check.isOverridden())
                .overriddenByName(overriddenBy == null ? null : overriddenBy.getFullName())
                .overrideReason(check.getOverrideReason())
                .overriddenAt(check.getOverriddenAt())
                .build();
    }

    private PromotionApprovalStepResponse toApprovalResponse(PromotionApprovalStep step) {
        Employee approver = step.getApprover();
        Employee originalApprover = step.getOriginalApprover();
        Employee reassignedBy = step.getReassignedBy();
        Employee delegatedBy = step.getDelegatedBy();
        Employee escalatedToApprover = step.getEscalatedToApprover();
        return PromotionApprovalStepResponse.builder()
                .id(step.getId())
                .stage(step.getStage())
                .decision(step.getDecision())
                .approverId(approver == null ? null : approver.getId())
                .approverName(approver == null ? null : approver.getFullName())
                .originalApproverId(originalApprover == null ? null : originalApprover.getId())
                .originalApproverName(originalApprover == null ? null : originalApprover.getFullName())
                .reassignedById(reassignedBy == null ? null : reassignedBy.getId())
                .reassignedByName(reassignedBy == null ? null : reassignedBy.getFullName())
                .reassignedAt(step.getReassignedAt())
                .reassignmentComment(step.getReassignmentComment())
                .delegatedById(delegatedBy == null ? null : delegatedBy.getId())
                .delegatedByName(delegatedBy == null ? null : delegatedBy.getFullName())
                .delegatedAt(step.getDelegatedAt())
                .delegationComment(step.getDelegationComment())
                .escalatedToApproverId(escalatedToApprover == null ? null : escalatedToApprover.getId())
                .escalatedToApproverName(escalatedToApprover == null ? null : escalatedToApprover.getFullName())
                .escalatedToGroup(step.getEscalatedToGroup())
                .lastReminderSentAt(step.getLastReminderSentAt())
                .reminderCount(step.getReminderCount())
                .escalatedAt(step.getEscalatedAt())
                .escalationComment(step.getEscalationComment())
                .approverGroup(step.getApproverGroup())
                .sequenceNumber(step.getSequenceNumber())
                .assignedAt(step.getAssignedAt())
                .actedAt(step.getActedAt())
                .comment(step.getComment())
                .build();
    }

    private PromotionActivityResponse toActivityResponse(PromotionActivityLog activity) {
        Employee actor = activity.getActor();
        return PromotionActivityResponse.builder()
                .id(activity.getId())
                .activityType(activity.getActivityType())
                .actorName(actor == null ? "System" : actor.getFullName())
                .description(activity.getDescription())
                .ipAddress(activity.getIpAddress())
                .userAgent(activity.getUserAgent())
                .deviceMetadata(activity.getDeviceMetadata())
                .createdAt(activity.getCreatedAt())
                .build();
    }

    private PromotionAttachmentResponse toAttachmentResponse(PromotionAttachment attachment) {
        Employee uploadedBy = attachment.getUploadedBy();
        return PromotionAttachmentResponse.builder()
                .id(attachment.getId())
                .documentType(attachment.getDocumentType())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .generatedTemplateKey(attachment.getGeneratedTemplateKey())
                .generatedTemplateName(attachment.getGeneratedTemplateName())
                .generatedTemplateVersion(attachment.getGeneratedTemplateVersion())
                .documentLibraryDocumentId(attachment.getDocumentLibraryDocumentId())
                .documentLibrarySyncedAt(attachment.getDocumentLibrarySyncedAt())
                .documentLibrarySyncError(attachment.getDocumentLibrarySyncError())
                .uploadedByName(uploadedBy == null ? "System" : uploadedBy.getFullName())
                .uploadedAt(attachment.getUploadedAt())
                .required(attachment.isRequired())
                .build();
    }

    private PromotionPayrollHandoffResponse toPayrollHandoffResponse(PromotionPayrollHandoff handoff) {
        Employee createdBy = handoff.getCreatedBy();
        Employee appliedBy = handoff.getAppliedBy();
        return PromotionPayrollHandoffResponse.builder()
                .id(handoff.getId())
                .status(handoff.getStatus())
                .effectiveDate(handoff.getEffectiveDate())
                .currentSalary(handoff.getCurrentSalary())
                .proposedSalary(handoff.getProposedSalary())
                .monthlyPayrollImpact(handoff.getMonthlyPayrollImpact())
                .annualPayrollImpact(handoff.getAnnualPayrollImpact())
                .payGroupName(handoff.getPayGroup() == null ? null : handoff.getPayGroup().getName())
                .createdByName(createdBy == null ? "System" : createdBy.getFullName())
                .appliedByName(appliedBy == null ? null : appliedBy.getFullName())
                .createdAt(handoff.getCreatedAt())
                .acknowledgedAt(handoff.getAcknowledgedAt())
                .appliedAt(handoff.getAppliedAt())
                .externalApplicationOnly(false)
                .note(handoff.getNote())
                .build();
    }

    private String nameOf(JobStep jobStep) {
        return jobStep == null ? null : jobStep.getName();
    }

    private String nameOf(Department department) {
        return department == null ? null : department.getName();
    }
}
