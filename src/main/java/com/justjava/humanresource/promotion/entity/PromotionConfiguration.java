package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "hr_promotion_configuration")
public class PromotionConfiguration {

    public static final BigDecimal DEFAULT_EXECUTIVE_APPROVAL_PERCENT_THRESHOLD = new BigDecimal("30.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name = "Default promotion policy";

    private Integer minimumMonthsInCurrentRole;

    @Column(precision = 7, scale = 2)
    private BigDecimal minimumAppraisalScore;

    @Column(nullable = false)
    private boolean allowProbationPromotion;

    @Column(nullable = false)
    private boolean requireFinanceApproval = true;

    @Column(nullable = false)
    private boolean requireExecutiveApproval = true;

    @Column(precision = 7, scale = 2)
    private BigDecimal maximumSalaryIncreasePercentWithoutExecutiveApproval =
            DEFAULT_EXECUTIVE_APPROVAL_PERCENT_THRESHOLD;

    private Integer approvalReminderAfterHours;

    private Integer approvalEscalationAfterHours;

    private Long escalationApproverId;

    @Column(length = 80)
    private String escalationApproverGroup;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "hr_promotion_configuration_required_submit_documents",
            joinColumns = @JoinColumn(name = "configuration_id")
    )
    @Column(name = "document_type", nullable = false, length = 60)
    private List<PromotionAttachmentType> requiredDocumentsBeforeSubmit = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "hr_promotion_configuration_required_implementation_documents",
            joinColumns = @JoinColumn(name = "configuration_id")
    )
    @Column(name = "document_type", nullable = false, length = 60)
    private List<PromotionAttachmentType> requiredDocumentsBeforeImplementation = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "hr_promotion_configuration_routing_rules",
            joinColumns = @JoinColumn(name = "configuration_id")
    )
    private List<PromotionRoutingRule> routingRules = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdByName;
    private String updatedByName;
    private LocalDateTime deactivatedAt;
    private String deactivatedByName;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
