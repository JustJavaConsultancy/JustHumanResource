package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.enums.PromotionRequestSource;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class PromotionDetailResponse {
    Long id;
    String requestNumber;
    Long employeeId;
    String employeeName;
    String employeeNumber;
    String employeeEmail;
    PromotionRequestSource requestSource;
    String currentDepartmentName;
    String proposedDepartmentName;
    String currentRoleName;
    String proposedRoleName;
    BigDecimal currentSalary;
    BigDecimal proposedSalary;
    BigDecimal salaryIncreaseAmount;
    BigDecimal salaryIncreasePercent;
    BigDecimal monthlyPayrollImpact;
    BigDecimal annualPayrollImpact;
    BigDecimal appraisalScore;
    String justification;
    PromotionStatus status;
    LocalDate effectiveDate;
    LocalDateTime submittedAt;
    LocalDateTime approvedAt;
    LocalDateTime rejectedAt;
    LocalDateTime implementedAt;
    String rejectionReason;
    String implementationComment;
    List<PromotionEligibilityCheckResponse> eligibilityChecks;
    List<PromotionApprovalStepResponse> approvalSteps;
    List<PromotionAttachmentResponse> attachments;
    List<PromotionAttachmentType> missingRequiredDocumentsBeforeSubmit;
    List<PromotionAttachmentType> missingRequiredDocumentsBeforeImplementation;
    PromotionPayrollHandoffResponse payrollHandoff;
    List<PromotionActivityResponse> activities;
}
