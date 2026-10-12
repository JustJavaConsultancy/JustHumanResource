package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PromotionConfigurationCommand {

    @NotBlank
    private String name;

    @Min(0)
    private Integer minimumMonthsInCurrentRole;

    @DecimalMin("0.00")
    private BigDecimal minimumAppraisalScore;

    private boolean allowProbationPromotion;

    private boolean requireFinanceApproval = true;

    private boolean requireExecutiveApproval = true;

    @DecimalMin("0.00")
    private BigDecimal maximumSalaryIncreasePercentWithoutExecutiveApproval;

    @Min(1)
    private Integer approvalReminderAfterHours;

    @Min(1)
    private Integer approvalEscalationAfterHours;

    private Long escalationApproverId;

    private String escalationApproverGroup;

    private List<PromotionAttachmentType> requiredDocumentsBeforeSubmit = new ArrayList<>();

    private List<PromotionAttachmentType> requiredDocumentsBeforeImplementation = new ArrayList<>();

    private List<PromotionRoutingRuleCommand> routingRules = new ArrayList<>();
}
