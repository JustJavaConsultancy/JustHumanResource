package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.promotion.enums.PromotionEligibilitySeverity;
import com.justjava.humanresource.hr.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "hr_promotion_eligibility_check",
        indexes = @Index(name = "idx_promotion_eligibility_request", columnList = "promotion_request_id")
)
public class PromotionEligibilityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promotion_request_id")
    private PromotionRequest promotionRequest;

    @Column(nullable = false, length = 80)
    private String checkCode;

    @Column(nullable = false, length = 120)
    private String checkName;

    private boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionEligibilitySeverity severity;

    @Column(length = 1000)
    private String message;

    private boolean overrideAllowed;
    private boolean overridden;

    @ManyToOne
    private Employee overriddenBy;

    @Column(length = 1000)
    private String overrideReason;

    private LocalDateTime overriddenAt;
    private LocalDateTime checkedAt;
}
