package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.LoanActivityResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanActivity;
import com.justjava.humanresource.loan.enums.LoanActivityType;

import java.util.List;

/** Audit trail for loan applications and accounts. Callers are responsible for access checks. */
public interface LoanActivityService {

    /** Records an application-level activity. Joins the caller's transaction. */
    EmployeeLoanActivity record(Long loanApplicationId, LoanActivityType type, String description, Long actorEmployeeId);

    /** Records an activity tied to an active loan account (activation, payroll, completion). */
    EmployeeLoanActivity recordForAccount(Long loanApplicationId, Long loanAccountId,
                                          LoanActivityType type, String description, Long actorEmployeeId);

    /** Oldest first. Actor name is "System" when there is no actor. */
    List<LoanActivityResponse> listForApplication(Long loanApplicationId);

    /** Internal: removes all activity rows of a hard-deleted draft. */
    void purge(Long loanApplicationId);
}
