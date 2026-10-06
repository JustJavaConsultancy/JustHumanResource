package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.loan.enums.LoanApprovalDecision;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "employee_loan_approval_steps",
        indexes = {
                @Index(name = "idx_loan_step_application", columnList = "loan_application_id"),
                @Index(name = "idx_loan_step_approver", columnList = "approver_employee_id, decision")
        }
)
public class EmployeeLoanApprovalStep extends BaseEntity {

    @Column(name = "loan_application_id", nullable = false)
    private Long loanApplicationId;

    @Column(nullable = false)
    private Integer sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanApprovalStage approvalStage;

    /** Set for CUSTOM steps (specific employee). */
    @Column(name = "approver_employee_id")
    private Long approverEmployeeId;

    /** Set for ROLE_BASED steps (candidate group, e.g. humanresource / financialofficers). */
    private String approverGroup;

    /** Null while the step is pending; set to APPROVE / REJECT / RETURN once acted on. */
    @Enumerated(EnumType.STRING)
    private LoanApprovalDecision decision;

    @Column(length = 2000)
    private String comments;

    private String flowableTaskId;
    private LocalDateTime decisionAt;
    private Long actedByEmployeeId;
}
