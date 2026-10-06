package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Shared status sets and approval-step helpers for the loan services (package-private). */
final class LoanApplicationSupport {

    private LoanApplicationSupport() {
    }

    /** Applicant may edit, upload and delete attachments, and submit. */
    static final Set<LoanApplicationStatus> EDITABLE =
            EnumSet.of(LoanApplicationStatus.DRAFT, LoanApplicationStatus.RETURNED_FOR_CORRECTION);

    /** Applicant may still cancel (provided no approver has approved yet). */
    static final Set<LoanApplicationStatus> PENDING_FIRST_STAGE = EnumSet.of(
            LoanApplicationStatus.SUBMITTED,
            LoanApplicationStatus.PENDING_HR_APPROVAL,
            LoanApplicationStatus.PENDING_CUSTOM_APPROVAL);

    /** Submitted and not yet active/closed: counts as "pending" exposure and has a current approval step. */
    static final Set<LoanApplicationStatus> IN_APPROVAL = EnumSet.of(
            LoanApplicationStatus.SUBMITTED,
            LoanApplicationStatus.PENDING_HR_APPROVAL,
            LoanApplicationStatus.PENDING_CUSTOM_APPROVAL,
            LoanApplicationStatus.HR_APPROVED,
            LoanApplicationStatus.PENDING_FINANCE_APPROVAL,
            LoanApplicationStatus.FINANCE_APPROVED,
            LoanApplicationStatus.CUSTOM_APPROVED);

    static String routeLabel(LoanApprovalRouteType type) {
        return type == LoanApprovalRouteType.CUSTOM ? "Custom approval path" : "Role-based HR then Finance";
    }

    /**
     * Steps of the most recent submission. Each submission creates a fresh batch whose
     * sequenceNo restarts at 1, so the latest attempt starts at the last step with sequenceNo 1.
     * Earlier attempts (after a return for correction) stay in the audit trail but are ignored here.
     */
    static List<EmployeeLoanApprovalStep> latestAttempt(List<EmployeeLoanApprovalStep> all) {
        List<EmployeeLoanApprovalStep> sorted = all.stream()
                .sorted(Comparator.comparing(EmployeeLoanApprovalStep::getId)).toList();
        int start = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (Integer.valueOf(1).equals(sorted.get(i).getSequenceNo())) {
                start = i;
            }
        }
        return sorted.subList(start, sorted.size());
    }

    /** First undecided step of the latest attempt. */
    static Optional<EmployeeLoanApprovalStep> currentPending(List<EmployeeLoanApprovalStep> all) {
        return latestAttempt(all).stream()
                .filter(s -> s.getDecision() == null)
                .min(Comparator.comparing(EmployeeLoanApprovalStep::getSequenceNo));
    }
}
