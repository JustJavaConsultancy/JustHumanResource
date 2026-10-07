package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanInterestType;
import com.justjava.humanresource.loan.enums.LoanRepaymentFrequency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
        name = "loan_products",
        uniqueConstraints = @UniqueConstraint(name = "uk_loan_product_code", columnNames = "code"),
        indexes = @Index(name = "idx_loan_product_active", columnList = "active")
)
public class LoanProduct extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal maximumAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumRepaymentAmount;

    @Column(nullable = false)
    private Integer maximumTenorMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanInterestType interestType = LoanInterestType.INTEREST_FREE;

    /** Annual percentage rate; zero when interestType is INTEREST_FREE. */
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanRepaymentFrequency repaymentFrequency = LoanRepaymentFrequency.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanApprovalRouteType approvalRouteType = LoanApprovalRouteType.ROLE_BASED;

    /** Required when approvalRouteType is CUSTOM. */
    @Column(name = "custom_approval_path_id")
    private Long customApprovalPathId;

    /**
     * How the approved principal is paid out. Locked once the product is used.
     * The column default lets ddl-auto=update add this NOT NULL column to a table that already has rows.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @ColumnDefault("'PAYROLL_PERIOD'")
    private LoanDisbursementMethod disbursementMethod = LoanDisbursementMethod.PAYROLL_PERIOD;

    @Column(nullable = false)
    private boolean requiresAttachment = false;

    @Column(nullable = false)
    private boolean active = true;

    /** Set true once any application references this product; locks financial/route edits. */
    @Column(nullable = false)
    private boolean used = false;
}