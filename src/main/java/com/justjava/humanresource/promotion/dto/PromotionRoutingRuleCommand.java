package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionRequestSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromotionRoutingRuleCommand {

    @Size(max = 100)
    private String name;

    @Min(1)
    private Integer priority = 100;

    private Long departmentId;

    private Long jobGradeId;

    @Size(max = 100)
    private String employeeCategory;

    private PromotionRequestSource requestSource;

    private Boolean financeApprovalRequired;

    private Boolean executiveApprovalRequired;
}
