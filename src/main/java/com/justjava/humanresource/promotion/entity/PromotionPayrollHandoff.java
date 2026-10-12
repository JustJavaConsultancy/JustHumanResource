package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.promotion.enums.PromotionPayrollHandoffStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "hr_promotion_payroll_handoff",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_promotion_payroll_handoff_request",
                columnNames = "promotion_request_id"
        )
)
public class PromotionPayrollHandoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private PromotionRequest promotionRequest;

    @ManyToOne(optional = false)
    private Employee employee;

    @ManyToOne
    private PayGroup payGroup;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal currentSalary;

    @Column(precision = 15, scale = 2)
    private BigDecimal proposedSalary;

    @Column(precision = 15, scale = 2)
    private BigDecimal monthlyPayrollImpact;

    @Column(precision = 15, scale = 2)
    private BigDecimal annualPayrollImpact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PromotionPayrollHandoffStatus status = PromotionPayrollHandoffStatus.PENDING;

    @ManyToOne
    private Employee createdBy;

    @ManyToOne
    private Employee appliedBy;

    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime appliedAt;
    private String note;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
