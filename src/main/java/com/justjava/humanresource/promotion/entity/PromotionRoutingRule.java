package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.promotion.enums.PromotionRequestSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class PromotionRoutingRule {

    @Column(length = 100)
    private String name;

    @Column(nullable = false)
    private Integer priority = 100;

    private Long departmentId;

    private Long jobGradeId;

    @Column(length = 100)
    private String employeeCategory;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private PromotionRequestSource requestSource;

    private Boolean financeApprovalRequired;

    private Boolean executiveApprovalRequired;
}
