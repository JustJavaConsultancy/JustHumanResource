package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.loan.enums.LoanApprovalDecision;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.service.LoanNotificationService;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import com.justjava.humanresource.utils.LoanEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Schedules each e-mail with {@link AfterCommitExecutor}; {@link LoanEmailService} builds and sends it in
 * its own read-only transaction. This class holds no transaction of its own on purpose.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanNotificationServiceImpl implements LoanNotificationService {

    private final AfterCommitExecutor afterCommit;
    private final LoanEmailService emails;

    @Override
    public void notifyApplicationSubmitted(Long loanApplicationId) {
        schedule(loanApplicationId, () -> emails.sendApplicationSubmitted(loanApplicationId));
    }

    @Override
    public void notifyHrApprovalPending(Long loanApplicationId) {
        schedule(loanApplicationId, () -> emails.sendHrApprovalPending(loanApplicationId));
    }

    @Override
    public void notifyFinanceApprovalPending(Long loanApplicationId) {
        schedule(loanApplicationId, () -> emails.sendFinanceApprovalPending(loanApplicationId));
    }

    @Override
    public void notifyCustomApprovalAssigned(Long loanApplicationId, Long approverEmployeeId) {
        if (approverEmployeeId == null) {
            log.warn("Loan email: custom approval notice skipped for application {} - no approver id.", loanApplicationId);
            return;
        }
        schedule(loanApplicationId, () -> emails.sendCustomApprovalAssigned(loanApplicationId, approverEmployeeId));
    }

    @Override
    public void notifyDecision(Long loanApplicationId, LoanApprovalStage stage, LoanApprovalDecision decision,
                               String comment) {
        schedule(loanApplicationId, () -> emails.sendDecision(loanApplicationId, stage, decision, comment));
    }

    @Override
    public void notifyLoanActivated(Long loanApplicationId) {
        schedule(loanApplicationId, () -> emails.sendLoanActivated(loanApplicationId));
    }

    @Override
    public void notifyLoanCompleted(Long loanApplicationId) {
        schedule(loanApplicationId, () -> emails.sendLoanCompleted(loanApplicationId));
    }

    @Override
    public void notifyMissedDeductions(Collection<Long> repaymentScheduleIds) {
        if (repaymentScheduleIds == null || repaymentScheduleIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(repaymentScheduleIds); // detach from the caller's collection
        afterCommit.runAfterCommit(() -> emails.sendMissedDeductions(ids));
    }

    private void schedule(Long loanApplicationId, Runnable send) {
        if (loanApplicationId == null) {
            log.warn("Loan email: notification skipped - no loan application id.");
            return;
        }
        afterCommit.runAfterCommit(send);
    }
}
