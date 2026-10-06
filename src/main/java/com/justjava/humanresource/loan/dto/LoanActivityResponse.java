package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanActivityType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** One row of the loan activity/audit timeline. */
@Value
@Builder
public class LoanActivityResponse {
    Long id;
    Long loanApplicationId;
    Long loanAccountId;
    LoanActivityType activityType;
    String description;
    Long actorEmployeeId;
    /** Null/"System" for automated events such as activation or payroll deductions. */
    String actorName;
    LocalDateTime createdAt;
}
