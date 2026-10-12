package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.promotion.enums.PromotionRequestSource;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "hr_promotion_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_promotion_request_number", columnNames = "request_number"),
        indexes = {
                @Index(name = "idx_promotion_employee", columnList = "employee_id"),
                @Index(name = "idx_promotion_status", columnList = "status"),
                @Index(name = "idx_promotion_effective_date", columnList = "effective_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, length = 50)
    private String requestNumber;

    private String processInstanceId;

    @ManyToOne(optional = false)
    private Employee employee;

    @ManyToOne
    private Employee requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PromotionRequestSource requestSource;

    @ManyToOne
    private JobStep currentRole;

    @ManyToOne(optional = false)
    private JobStep proposedRole;

    @ManyToOne
    private Department currentDepartment;

    @ManyToOne
    private Department proposedDepartment;

    @ManyToOne
    private Employee currentManager;

    @ManyToOne
    private Employee proposedManager;

    @Column(precision = 15, scale = 2)
    private BigDecimal currentSalary;

    @Column(precision = 15, scale = 2)
    private BigDecimal proposedSalary;

    @Column(precision = 15, scale = 2)
    private BigDecimal salaryIncreaseAmount;

    @Column(precision = 7, scale = 2)
    private BigDecimal salaryIncreasePercent;

    @Column(precision = 15, scale = 2)
    private BigDecimal monthlyPayrollImpact;

    @Column(precision = 15, scale = 2)
    private BigDecimal annualPayrollImpact;

    @ManyToOne
    private EmployeeAppraisal appraisal;

    @Column(length = 3000)
    private String justification;

    @Column(precision = 7, scale = 2)
    private BigDecimal appraisalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PromotionStatus status;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    private String lastApprovalComment;
    private String rejectionReason;
    private String implementationComment;

    private LocalDateTime submittedAt;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime implementedAt;
}
