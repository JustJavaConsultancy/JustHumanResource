package com.justjava.humanresource.utils;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.loan.entity.EmployeeLoanAccount;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.entity.LoanDisbursement;
import com.justjava.humanresource.loan.entity.LoanRepaymentSchedule;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalDecision;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanAccountRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApprovalStepRepository;
import com.justjava.humanresource.loan.repository.LoanDisbursementRepository;
import com.justjava.humanresource.loan.repository.LoanRepaymentScheduleRepository;
import com.justjava.humanresource.loan.service.LoanApprovalRouteService;
import com.justjava.humanresource.orgStructure.entity.Company;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds and sends the employee-loan e-mails, in the same style as {@link RequestEmailService}.
 *
 * Called only through {@code LoanNotificationService}, which runs these methods after the business
 * transaction has committed. Each method opens its own read-only transaction (so lazy associations
 * load on the notification thread), re-reads current data, and never throws: a failed send is logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanEmailService {

    private static final String DEFAULT_COMPANY_NAME = "Human Resources";
    private static final int MAX_DIGEST_ROWS = 50;

    private final EmailService emailService;
    private final EmployeeRepository employees;
    private final EmployeeLoanApplicationRepository applications;
    private final EmployeeLoanApprovalStepRepository steps;
    private final EmployeeLoanAccountRepository accounts;
    private final LoanRepaymentScheduleRepository schedules;
    private final LoanDisbursementRepository disbursements;

    private record Line(String label, String value) {
    }

    private record Mail(String subject, String html, String text) {
    }

    // =====================================================================
    // Employee: submission
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendApplicationSubmitted(Long applicationId) {
        guarded("application submitted", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null) return;
            Employee employee = app.getEmployee();

            List<String> paragraphs = new ArrayList<>();
            paragraphs.add("Your loan application " + app.getApplicationNumber() + " for " + productName(app)
                    + " has been received and is now in the approval process.");
            String approver = currentApproverName(applicationId);
            if (!approver.isBlank()) {
                paragraphs.add("It is currently with " + approver + " for approval.");
            }
            paragraphs.add("You will be notified when a decision is made.");

            sendTo(employee, compose("Loan application received", employee, company(employee),
                    paragraphs, applicationLines(app)), "loan submission notice");
        });
    }

    // =====================================================================
    // Approvers: pending work
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendHrApprovalPending(Long applicationId) {
        guarded("HR approval pending", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null || app.getStatus() != LoanApplicationStatus.PENDING_HR_APPROVAL) return;
            notifyGroup(app, LoanApprovalRouteService.HR_GROUP, "Loan application awaiting HR approval",
                    "HR approval");
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendFinanceApprovalPending(Long applicationId) {
        guarded("Finance approval pending", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null || app.getStatus() != LoanApplicationStatus.PENDING_FINANCE_APPROVAL) return;
            notifyGroup(app, LoanApprovalRouteService.FINANCE_GROUP, "Loan application awaiting Finance approval",
                    "Finance approval");
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendCustomApprovalAssigned(Long applicationId, Long approverEmployeeId) {
        guarded("custom approval assigned", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null || app.getStatus() != LoanApplicationStatus.PENDING_CUSTOM_APPROVAL) return;
            Employee approver = employees.findById(approverEmployeeId).orElse(null);
            if (approver == null) {
                log.warn("Loan email: custom approver {} for application {} could not be found.",
                        approverEmployeeId, applicationId);
                return;
            }
            Employee applicant = app.getEmployee();
            List<String> paragraphs = List.of(
                    "A loan application from " + applicant.getFullName() + " (" + app.getApplicationNumber()
                            + ") has been assigned to you for approval.",
                    "Please log in to review the details and record your decision.");
            sendTo(approver, compose("Loan application assigned to you for approval", approver, company(applicant),
                    paragraphs, applicationLines(app)), "custom approval assignment notice");
        });
    }

    // =====================================================================
    // Employee: decisions
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendDecision(Long applicationId, LoanApprovalStage stage, LoanApprovalDecision decision,
                             String comment) {
        guarded("approval decision", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null) return;
            Employee employee = app.getEmployee();
            String cleanComment = comment == null || comment.isBlank() ? null : comment.trim();
            String who = decidedBy(app, stage);

            String subject;
            List<String> paragraphs = new ArrayList<>();
            String subjectRef = "Your loan application " + app.getApplicationNumber() + " for " + productName(app);
            switch (decision) {
                case APPROVE -> {
                    subject = "Loan application approved by " + who;
                    paragraphs.add(subjectRef + " has been approved by " + who + ".");
                    String next = nextStepSentence(app);
                    if (!next.isBlank()) paragraphs.add(next);
                }
                case REJECT -> {
                    subject = "Loan application rejected";
                    paragraphs.add(subjectRef + " has been rejected by " + who + ".");
                    if (cleanComment != null) paragraphs.add("Reason: " + cleanComment);
                }
                case RETURN -> {
                    subject = "Loan application returned for correction";
                    paragraphs.add(subjectRef + " has been returned for correction by " + who + ".");
                    if (cleanComment != null) paragraphs.add("Comment: " + cleanComment);
                    paragraphs.add("Please log in, update the application and resubmit it when ready.");
                }
                default -> {
                    return;
                }
            }
            sendTo(employee, compose(subject, employee, company(employee), paragraphs, applicationLines(app)),
                    "loan " + decision.name().toLowerCase(Locale.ROOT) + " notice");
        });
    }

    // =====================================================================
    // Employee: activation and completion
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendLoanActivated(Long applicationId) {
        guarded("loan activated", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null) return;
            if (disbursementOf(applicationId) != null) {
                // Loans with a disbursement row get a disbursement-specific mail (payroll scheduled /
                // payment confirmed) that already says the loan is active, so skip the generic one.
                return;
            }
            EmployeeLoanAccount account = accounts.findByLoanApplicationId(applicationId).orElse(null);
            if (account == null) {
                log.warn("Loan email: no loan account for application {}; activation notice skipped.", applicationId);
                return;
            }
            Employee employee = app.getEmployee();

            List<Line> lines = new ArrayList<>();
            lines.add(new Line("Application number", app.getApplicationNumber()));
            lines.add(new Line("Loan product", productName(app)));
            lines.add(new Line("Principal", money(account.getPrincipalAmount())));
            lines.add(new Line("Total repayable", money(account.getTotalRepayableAmount())));
            lines.add(new Line("Monthly deduction", money(account.getRepaymentAmount())));
            lines.add(new Line("Number of installments", String.valueOf(account.getTenorMonths())));
            lines.add(new Line("First deduction month", String.valueOf(account.getRepaymentStartMonth())));

            sendTo(employee, compose("Loan activated", employee, company(employee), List.of(
                            "Your loan " + app.getApplicationNumber() + " has been approved and is now active.",
                            "Repayments will be deducted from your monthly payroll as shown below."), lines),
                    "loan activated notice");
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendLoanCompleted(Long applicationId) {
        guarded("loan completed", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null) return;
            EmployeeLoanAccount account = accounts.findByLoanApplicationId(applicationId).orElse(null);
            Employee employee = app.getEmployee();

            List<Line> lines = new ArrayList<>();
            lines.add(new Line("Application number", app.getApplicationNumber()));
            lines.add(new Line("Loan product", productName(app)));
            if (account != null) {
                lines.add(new Line("Total repaid", money(account.getTotalPaidAmount())));
            }
            sendTo(employee, compose("Loan fully repaid", employee, company(employee), List.of(
                    "Your loan " + app.getApplicationNumber() + " has been fully repaid.",
                    "No further loan deductions will be made for it."), lines), "loan completed notice");
        });
    }

    // =====================================================================
    // Disbursement: outside-payroll payment and payroll scheduling
    // =====================================================================

    /** Employee: all approvals done, Finance now has to pay the loan outside payroll. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendExternalPaymentPendingToEmployee(Long applicationId) {
        guarded("external payment pending (employee)", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null || app.getStatus() != LoanApplicationStatus.PENDING_DISBURSEMENT) return;
            LoanDisbursement d = disbursementOf(applicationId);
            if (d == null || d.getMethod() != LoanDisbursementMethod.OUTSIDE_PAYROLL) return;
            Employee employee = app.getEmployee();

            List<String> paragraphs = List.of(
                    "Your loan application " + app.getApplicationNumber() + " for " + productName(app)
                            + " has received all approvals.",
                    "This loan is paid outside the payroll system, so Finance will now arrange payment to your bank account.",
                    "Your repayment schedule starts only after the payment is confirmed. You will be notified when that happens.");
            sendTo(employee, compose("Loan approved - awaiting payment", employee, company(employee),
                    paragraphs, disbursementLines(app, d)), "external payment pending notice");
        });
    }

    /** Finance approvers: an outside-payroll loan is waiting to be paid and confirmed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendExternalPaymentPendingToFinance(Long applicationId) {
        guarded("external payment pending (Finance)", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null || app.getStatus() != LoanApplicationStatus.PENDING_DISBURSEMENT) return;
            LoanDisbursement d = disbursementOf(applicationId);
            if (d == null || d.getStatus() != LoanDisbursementStatus.PENDING_EXTERNAL_PAYMENT) return;

            Employee applicant = app.getEmployee();
            List<Employee> recipients = approvers(LoanApprovalRouteService.FINANCE_GROUP).stream()
                    .filter(e -> !Objects.equals(e.getId(), applicant.getId())) // nobody confirms their own loan
                    .toList();
            if (recipients.isEmpty()) {
                log.warn("Loan email: no active Finance recipients found for pending payment of application {}.",
                        app.getApplicationNumber());
                return;
            }
            String companyName = companyName(company(applicant));
            for (Employee recipient : recipients) {
                List<String> paragraphs = List.of(
                        "The loan application from " + applicant.getFullName() + " (" + app.getApplicationNumber()
                                + ") is fully approved and is waiting for external payment of "
                                + money(d.getAmount()) + ".",
                        "After paying the employee, please open the Finance disbursement queue and confirm the payment "
                                + "so the loan can be activated.");
                sendTo(recipient, compose("Loan payment awaiting confirmation", recipient, companyName,
                        paragraphs, disbursementLines(app, d)), "external payment pending Finance notice");
            }
        });
    }

    /** Employee: Finance confirmed the payment; the loan is active and the schedule exists. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendExternalPaymentConfirmed(Long applicationId) {
        guarded("external payment confirmed", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null) return;
            LoanDisbursement d = disbursementOf(applicationId);
            if (d == null || d.getStatus() != LoanDisbursementStatus.PAID) return;
            EmployeeLoanAccount account = accounts.findByLoanApplicationId(applicationId).orElse(null);
            Employee employee = app.getEmployee();

            LocalDate effective = account != null ? account.getRepaymentStartMonth() : d.getEffectiveRepaymentStartMonth();
            LocalDate selected = d.getSelectedRepaymentStartMonth();

            List<String> paragraphs = new ArrayList<>();
            paragraphs.add("Finance has confirmed payment of your loan " + app.getApplicationNumber() + " for "
                    + productName(app) + ". Your loan is now active.");
            if (startWasAdjusted(selected, effective)) {
                paragraphs.add("Your first deduction month is " + YearMonth.from(effective) + " instead of the "
                        + YearMonth.from(selected) + " you selected, because repayment cannot start in the month "
                        + "the loan was paid.");
            }
            paragraphs.add("Repayments will be deducted from your monthly payroll as shown below.");

            List<Line> lines = new ArrayList<>();
            lines.add(new Line("Application number", app.getApplicationNumber()));
            lines.add(new Line("Loan product", productName(app)));
            lines.add(new Line("Amount paid", money(d.getAmount())));
            if (d.getPaidAt() != null) lines.add(new Line("Paid on", String.valueOf(d.getPaidAt().toLocalDate())));
            if (d.getPaymentReference() != null && !d.getPaymentReference().isBlank()) {
                lines.add(new Line("Payment reference", d.getPaymentReference()));
            }
            addBankLines(lines, d);
            addRepaymentLines(lines, account, effective);

            sendTo(employee, compose("Loan payment confirmed - loan active", employee, company(employee),
                    paragraphs, lines), "external payment confirmed notice");
        });
    }

    /** Employee: payroll-period loan is approved, active, and will be paid through payroll. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendPayrollDisbursementScheduled(Long applicationId) {
        guarded("payroll disbursement scheduled", applicationId, () -> {
            EmployeeLoanApplication app = load(applicationId);
            if (app == null) return;
            LoanDisbursement d = disbursementOf(applicationId);
            if (d == null || d.getMethod() != LoanDisbursementMethod.PAYROLL_PERIOD) return;
            EmployeeLoanAccount account = accounts.findByLoanApplicationId(applicationId).orElse(null);
            Employee employee = app.getEmployee();

            LocalDate effective = account != null ? account.getRepaymentStartMonth() : d.getEffectiveRepaymentStartMonth();
            LocalDate selected = d.getSelectedRepaymentStartMonth();

            List<String> paragraphs = new ArrayList<>();
            paragraphs.add("Your loan application " + app.getApplicationNumber() + " for " + productName(app)
                    + " has received all approvals and is now active.");
            paragraphs.add(d.getDisbursementMonth() != null
                    ? "The loan amount will be paid to you through payroll for " + YearMonth.from(d.getDisbursementMonth()) + "."
                    : "The loan amount will be paid to you through payroll.");
            if (startWasAdjusted(selected, effective)) {
                paragraphs.add("Your first deduction month is " + YearMonth.from(effective) + " instead of the "
                        + YearMonth.from(selected) + " you selected, because repayment cannot start in the month "
                        + "the loan is paid.");
            }

            List<Line> lines = new ArrayList<>();
            lines.add(new Line("Application number", app.getApplicationNumber()));
            lines.add(new Line("Loan product", productName(app)));
            lines.add(new Line("Loan amount", money(d.getAmount())));
            addRepaymentLines(lines, account, effective);

            sendTo(employee, compose("Loan approved - paid through payroll", employee, company(employee),
                    paragraphs, lines), "payroll disbursement scheduled notice");
        });
    }

    // =====================================================================
    // HR + Finance: missed deductions
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendMissedDeductions(Collection<Long> scheduleIds) {
        try {
            List<LoanRepaymentSchedule> rows = schedules.findAllById(scheduleIds).stream()
                    .filter(r -> r.getStatus() == LoanRepaymentStatus.MISSED)
                    .sorted(Comparator.comparing(LoanRepaymentSchedule::getDueMonth)
                            .thenComparing(LoanRepaymentSchedule::getLoanAccountId))
                    .toList();
            if (rows.isEmpty()) return;

            Map<Long, EmployeeLoanAccount> accountById = accounts
                    .findAllById(rows.stream().map(LoanRepaymentSchedule::getLoanAccountId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(EmployeeLoanAccount::getId, a -> a));
            Map<Long, EmployeeLoanApplication> appById = applications
                    .findAllById(accountById.values().stream().map(EmployeeLoanAccount::getLoanApplicationId)
                            .collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(EmployeeLoanApplication::getId, a -> a));

            List<String> detail = new ArrayList<>();
            Employee firstEmployee = null;
            for (LoanRepaymentSchedule r : rows) {
                EmployeeLoanAccount account = accountById.get(r.getLoanAccountId());
                EmployeeLoanApplication app = account == null ? null : appById.get(account.getLoanApplicationId());
                if (app == null) continue;
                if (firstEmployee == null) firstEmployee = app.getEmployee();
                detail.add(app.getEmployee().getFullName() + " - " + app.getApplicationNumber() + " ("
                        + productName(app) + "): installment " + r.getSequenceNumber() + " due "
                        + r.getDueMonth() + ", outstanding " + money(r.getOutstandingAmount()));
            }
            if (detail.isEmpty()) return;

            String companyName = firstEmployee == null ? DEFAULT_COMPANY_NAME : companyName(company(firstEmployee));
            int shown = Math.min(detail.size(), MAX_DIGEST_ROWS);
            List<String> listed = detail.subList(0, shown);
            String extra = detail.size() > shown ? "... and " + (detail.size() - shown) + " more. Open the loan missed-deductions view for the full list." : null;
            String subject = detail.size() == 1 ? "Loan deduction missed" : detail.size() + " loan deductions missed";

            for (Employee recipient : approvers(LoanApprovalRouteService.HR_GROUP, LoanApprovalRouteService.FINANCE_GROUP)) {
                sendTo(recipient, composeDigest(subject, recipient, companyName,
                        "The following loan installments were due but could not be deducted from payroll:",
                        listed, extra), "missed deduction notice");
            }
        } catch (Exception e) {
            log.warn("Loan email: missed deduction notice failed: {}", e.getMessage(), e);
        }
    }

    // =====================================================================
    // Building blocks
    // =====================================================================

    private void guarded(String what, Long applicationId, Runnable work) {
        try {
            work.run();
        } catch (Exception e) {
            log.warn("Loan email: {} notice for application {} failed: {}", what, applicationId, e.getMessage(), e);
        }
    }

    private EmployeeLoanApplication load(Long applicationId) {
        EmployeeLoanApplication app = applications.findById(applicationId).orElse(null);
        if (app == null) {
            log.warn("Loan email: application {} not found; notice skipped.", applicationId);
        }
        return app;
    }

    private LoanDisbursement disbursementOf(Long applicationId) {
        return disbursements.findByLoanApplicationId(applicationId).orElse(null);
    }

    private static boolean startWasAdjusted(LocalDate selected, LocalDate effective) {
        return selected != null && effective != null && !YearMonth.from(selected).equals(YearMonth.from(effective));
    }

    private List<Line> disbursementLines(EmployeeLoanApplication app, LoanDisbursement d) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Application number", app.getApplicationNumber()));
        lines.add(new Line("Employee", app.getEmployee().getFullName()));
        lines.add(new Line("Loan product", productName(app)));
        lines.add(new Line("Approved amount", money(d.getAmount())));
        addBankLines(lines, d);
        return lines;
    }

    private void addRepaymentLines(List<Line> lines, EmployeeLoanAccount account, LocalDate effective) {
        if (account != null) {
            lines.add(new Line("Total repayable", money(account.getTotalRepayableAmount())));
            lines.add(new Line("Monthly deduction", money(account.getRepaymentAmount())));
            lines.add(new Line("Number of installments", String.valueOf(account.getTenorMonths())));
        }
        if (effective != null) {
            lines.add(new Line("First deduction month", String.valueOf(YearMonth.from(effective))));
        }
    }

    /** Account number is masked: e-mail is not a safe place for full bank details. */
    private void addBankLines(List<Line> lines, LoanDisbursement d) {
        if (d.getBankNameSnapshot() != null && !d.getBankNameSnapshot().isBlank()) {
            lines.add(new Line("Bank", d.getBankNameSnapshot()));
        }
        if (d.getAccountNameSnapshot() != null && !d.getAccountNameSnapshot().isBlank()) {
            lines.add(new Line("Account name", d.getAccountNameSnapshot()));
        }
        String number = d.getAccountNumberSnapshot() == null ? "" : d.getAccountNumberSnapshot().trim();
        if (!number.isEmpty()) {
            lines.add(new Line("Account number",
                    number.length() <= 4 ? number : "****" + number.substring(number.length() - 4)));
        }
    }

    private void notifyGroup(EmployeeLoanApplication app, String group, String subject, String taskName) {
        Employee applicant = app.getEmployee();
        List<Employee> recipients = approvers(group).stream()
                .filter(e -> !Objects.equals(e.getId(), applicant.getId())) // nobody decides their own loan
                .toList();
        if (recipients.isEmpty()) {
            log.warn("Loan email: no active recipients found in group '{}' for application {}.",
                    group, app.getApplicationNumber());
            return;
        }
        String companyName = companyName(company(applicant));
        for (Employee recipient : recipients) {
            List<String> paragraphs = List.of(
                    "A loan application from " + applicant.getFullName() + " (" + app.getApplicationNumber()
                            + ") is awaiting " + taskName + ".",
                    "Please log in to review the details and record your decision.");
            sendTo(recipient, compose(subject, recipient, companyName, paragraphs, applicationLines(app)),
                    "pending " + taskName + " notice");
        }
    }

    /** Active, e-mailable employees in any of the groups, de-duplicated by e-mail address. */
    private List<Employee> approvers(String... groups) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (String g : groups) {
            String lower = g.toLowerCase(Locale.ROOT);
            names.add(lower);
            names.add("/" + lower);
        }
        Map<String, Employee> byEmail = new LinkedHashMap<>();
        for (Employee e : employees.findActiveEmployeesInAnyGroupIgnoreCase(names)) {
            if (e.getEmail() != null && !e.getEmail().isBlank()) {
                byEmail.putIfAbsent(e.getEmail().trim().toLowerCase(Locale.ROOT), e);
            }
        }
        return new ArrayList<>(byEmail.values());
    }

    /** Latest submission only: earlier (returned) attempts restart at sequenceNo 1 and stay as audit. */
    private List<EmployeeLoanApprovalStep> latestAttempt(Long applicationId) {
        List<EmployeeLoanApprovalStep> sorted = steps.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(applicationId)
                .stream().sorted(Comparator.comparing(EmployeeLoanApprovalStep::getId)).toList();
        int start = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (Integer.valueOf(1).equals(sorted.get(i).getSequenceNo())) start = i;
        }
        return sorted.subList(start, sorted.size());
    }

    private String currentApproverName(Long applicationId) {
        return latestAttempt(applicationId).stream()
                .filter(s -> s.getDecision() == null && s.getApproverEmployeeId() != null)
                .min(Comparator.comparing(EmployeeLoanApprovalStep::getSequenceNo))
                .flatMap(s -> employees.findById(s.getApproverEmployeeId()))
                .map(this::displayName)
                .orElse("");
    }

    /** "HR", "Finance", or the custom approver's name. */
    private String decidedBy(EmployeeLoanApplication app, LoanApprovalStage stage) {
        return switch (stage) {
            case HR -> "HR";
            case FINANCE -> "Finance";
            case CUSTOM -> latestAttempt(app.getId()).stream()
                    .filter(s -> s.getApprovalStage() == LoanApprovalStage.CUSTOM && s.getDecision() != null
                            && s.getActedByEmployeeId() != null)
                    .max(Comparator.comparing(EmployeeLoanApprovalStep::getSequenceNo))
                    .flatMap(s -> employees.findById(s.getActedByEmployeeId()))
                    .map(this::displayName)
                    .filter(n -> !n.isBlank())
                    .orElse("your approver");
        };
    }

    private String nextStepSentence(EmployeeLoanApplication app) {
        return switch (app.getStatus()) {
            case PENDING_FINANCE_APPROVAL -> "It is now awaiting Finance approval.";
            case PENDING_CUSTOM_APPROVAL -> {
                String name = currentApproverName(app.getId());
                yield name.isBlank() ? "It is now awaiting the next approver."
                        : "It is now with " + name + " for approval.";
            }
            case FINANCE_APPROVED, CUSTOM_APPROVED ->
                    "All approvals are complete and the loan is being activated.";
            case ACTIVE -> "All approvals are complete and the loan is now active.";
            default -> "";
        };
    }

    private List<Line> applicationLines(EmployeeLoanApplication app) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Application number", app.getApplicationNumber()));
        lines.add(new Line("Employee", app.getEmployee().getFullName()));
        lines.add(new Line("Loan product", productName(app)));
        lines.add(new Line("Requested amount", money(app.getRequestedAmount())));
        lines.add(new Line("Monthly repayment", money(app.getRepaymentAmount())));
        lines.add(new Line("Tenor (months)", String.valueOf(app.getTenorMonths())));
        lines.add(new Line("First deduction month", String.valueOf(app.getRepaymentStartMonth())));
        return lines;
    }

    private String productName(EmployeeLoanApplication app) {
        return app.getLoanProduct() == null ? "loan" : app.getLoanProduct().getName();
    }

    private static String money(BigDecimal value) {
        return value == null ? "-" : String.format(Locale.US, "%,.2f", value);
    }

    private Company company(Employee employee) {
        return employee != null && employee.getDepartment() != null ? employee.getDepartment().getCompany() : null;
    }

    private String companyName(Company company) {
        return company != null && company.getName() != null && !company.getName().isBlank()
                ? company.getName() : DEFAULT_COMPANY_NAME;
    }

    private String displayName(Employee employee) {
        String first = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String last = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isBlank() && employee.getFullName() != null ? employee.getFullName().trim() : name;
    }

    // ---------------------------------------------------------------- rendering

    private Mail compose(String subject, Employee recipient, Company company, List<String> paragraphs,
                         List<Line> lines) {
        return compose(subject, recipient, companyName(company), paragraphs, lines);
    }

    private Mail compose(String subject, Employee recipient, String companyName, List<String> paragraphs,
                         List<Line> lines) {
        String name = recipient.getFullName();
        StringBuilder text = new StringBuilder("Dear ").append(name).append(",\n\n");
        StringBuilder html = new StringBuilder("<p>Dear ").append(html(name)).append(",</p>");
        for (String p : paragraphs) {
            text.append(p).append("\n\n");
            html.append("<p>").append(html(p)).append("</p>");
        }
        if (lines != null && !lines.isEmpty()) {
            html.append("<table style=\"border-collapse:collapse\">");
            for (Line l : lines) {
                text.append(l.label()).append(": ").append(l.value()).append("\n");
                html.append("<tr><td style=\"padding:2px 12px 2px 0\"><strong>").append(html(l.label()))
                        .append("</strong></td><td>").append(html(l.value())).append("</td></tr>");
            }
            html.append("</table>");
            text.append("\n");
        }
        text.append("Regards,\n").append(companyName);
        html.append("<p>Regards,<br><strong>").append(html(companyName)).append("</strong></p>");
        return new Mail(subject, html.toString(), text.toString());
    }

    private Mail composeDigest(String subject, Employee recipient, String companyName, String intro,
                               List<String> rows, String extra) {
        String name = recipient.getFullName();
        StringBuilder text = new StringBuilder("Dear ").append(name).append(",\n\n").append(intro).append("\n\n");
        StringBuilder html = new StringBuilder("<p>Dear ").append(html(name)).append(",</p><p>")
                .append(html(intro)).append("</p><ul>");
        for (String r : rows) {
            text.append("- ").append(r).append("\n");
            html.append("<li>").append(html(r)).append("</li>");
        }
        html.append("</ul>");
        if (extra != null) {
            text.append(extra).append("\n");
            html.append("<p>").append(html(extra)).append("</p>");
        }
        text.append("\nRegards,\n").append(companyName);
        html.append("<p>Regards,<br><strong>").append(html(companyName)).append("</strong></p>");
        return new Mail(subject, html.toString(), text.toString());
    }

    private void sendTo(Employee recipient, Mail mail, String context) {
        String email = recipient.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Loan email: skipping {} for employee {} - no email address on file.", context, recipient.getId());
            return;
        }
        try {
            emailService.sendEmail(email.trim(), mail.subject(), mail.html(), mail.text());
        } catch (Exception e) {
            log.warn("Loan email: failed to send {} to employee {}: {}", context, recipient.getId(), e.getMessage());
        }
    }

    private String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}