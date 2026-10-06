package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.enums.LoanApprovalDecision;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;

import java.util.Collection;

/**
 * Lifecycle e-mails for employee loans.
 *
 * Every method is safe to call from inside a transaction (including a Flowable delegate):
 * the e-mail is scheduled to run <b>after the surrounding transaction commits</b>, on the notification
 * executor, and it never throws. A rolled-back transaction therefore sends nothing, and a failed
 * e-mail can never roll back a submission, approval, activation or payroll posting.
 *
 * Methods take ids, not entities, because the work runs later on another thread.
 */
public interface LoanNotificationService {

    /** Employee: application submitted (or resubmitted) and now in approval. */
    void notifyApplicationSubmitted(Long loanApplicationId);

    /** HR approvers: an application is waiting for the role-based HR approval. */
    void notifyHrApprovalPending(Long loanApplicationId);

    /** Finance approvers: HR has approved; the application is waiting for Finance. */
    void notifyFinanceApprovalPending(Long loanApplicationId);

    /** The named custom approver: a custom-path task has just been assigned to them. */
    void notifyCustomApprovalAssigned(Long loanApplicationId, Long approverEmployeeId);

    /** Employee: an HR / Finance / custom approver approved, rejected or returned the application. */
    void notifyDecision(Long loanApplicationId, LoanApprovalStage stage, LoanApprovalDecision decision,
                        String comment);

    /** Employee: the loan is active and the repayment schedule exists. */
    void notifyLoanActivated(Long loanApplicationId);

    /** Employee: the loan is fully repaid. */
    void notifyLoanCompleted(Long loanApplicationId);

    /**
     * HR and Finance: one digest listing installments that were flagged MISSED.
     * Only rows that are MISSED when the e-mail is built are listed.
     */
    void notifyMissedDeductions(Collection<Long> repaymentScheduleIds);
}
