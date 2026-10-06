package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "employee_loan_activities",
        indexes = @Index(name = "idx_loan_activity_application", columnList = "loan_application_id")
)
public class EmployeeLoanActivity extends BaseEntity {

    @Column(name = "loan_application_id")
    private Long loanApplicationId;

    /** Optional: set for activities tied to an active loan account (payroll, completion, closure). */
    @Column(name = "loan_account_id")
    private Long loanAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanActivityType activityType;

    private Long actorEmployeeId;

    @Column(length = 2000)
    private String description;
}
