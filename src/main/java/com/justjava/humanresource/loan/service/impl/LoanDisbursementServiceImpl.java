package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.loan.dto.LoanConfirmDisbursementCommand;
import com.justjava.humanresource.loan.dto.LoanDisbursementFilter;
import com.justjava.humanresource.loan.dto.LoanDisbursementResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanAccount;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.LoanDisbursement;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.LoanDisbursementRepository;
import com.justjava.humanresource.loan.service.EmployeeLoanAccountService;
import com.justjava.humanresource.loan.service.LoanActivityService;
import com.justjava.humanresource.loan.service.LoanDisbursementService;
import com.justjava.humanresource.loan.service.LoanNotificationService;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanDisbursementServiceImpl implements LoanDisbursementService {

    private static final int MAX_REFERENCE_LENGTH = 100;
    private static final int MAX_COMMENT_LENGTH = 2000;

    private static final String[] CSV_HEADER = {
            "Application Number", "Employee Number", "Employee Name", "Department", "Loan Product",
            "Approved Amount", "Bank Name", "Account Name", "Account Number", "Approval Route",
            "Final Approved At", "Selected Repayment Start", "Effective Repayment Start",
            "Disbursement Status", "Paid At", "Paid By", "Payment Reference", "Comment"
    };

    private final LoanDisbursementRepository disbursements;
    private final EmployeeLoanApplicationRepository applications;
    private final EmployeeLoanAccountService accountService;
    private final LoanActivityService activityService;
    private final LoanNotificationService notifications;
    private final PayrollPeriodService payrollPeriodService;
    private final EmployeeRepository employeeRepository;
    private final AuthenticationManager authenticationManager;
    private final PayrollMessageDispatcher payrollMessageDispatcher;
    private final AfterCommitExecutor afterCommitExecutor;

    // =====================================================================================
    // Repayment start rule
    // =====================================================================================

    /**
     * effective = max(selected month, month after the month the money was actually disbursed).
     * Both results are the first day of a month. Repayment can never start in the disbursement month.
     */
    public static LocalDate calculateEffectiveRepaymentStart(LocalDate selectedStartMonth, LocalDate disbursementDate) {
        if (selectedStartMonth == null || disbursementDate == null) {
            throw new IllegalArgumentException("Selected repayment start and disbursement date are required.");
        }
        LocalDate selected = selectedStartMonth.withDayOfMonth(1);
        LocalDate earliestAllowed = YearMonth.from(disbursementDate).plusMonths(1).atDay(1);
        return selected.isBefore(earliestAllowed) ? earliestAllowed : selected;
    }

    // =====================================================================================
    // Final approval
    // =====================================================================================

    @Override
    @Transactional
    public void handleFinalApproval(Long loanApplicationId, Long finalApproverEmployeeId) {
        EmployeeLoanApplication app = applications.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException("Loan application not found: " + loanApplicationId));

        Optional<LoanDisbursement> existing = disbursements.findByLoanApplicationId(loanApplicationId);
        if (existing.isPresent()) {
            resumeIfIncomplete(app, existing.get());
            return; // idempotent
        }

        if (app.getStatus() != LoanApplicationStatus.FINANCE_APPROVED
                && app.getStatus() != LoanApplicationStatus.CUSTOM_APPROVED) {
            throw new IllegalStateException("Loan " + app.getApplicationNumber()
                    + " is not awaiting disbursement handling (status " + app.getStatus() + ").");
        }

        // Applications submitted before the disbursement feature have no snapshot: they were always payroll loans.
        LoanDisbursementMethod method = app.getDisbursementMethodSnapshot();
        if (method == null) {
            method = LoanDisbursementMethod.PAYROLL_PERIOD;
            app.setDisbursementMethodSnapshot(method);
            log.info("Loan {} has no disbursement method snapshot; treating as PAYROLL_PERIOD.",
                    app.getApplicationNumber());
        }

        LocalDateTime approvedAt = app.getFinalApprovedAt() != null ? app.getFinalApprovedAt() : LocalDateTime.now();
        Long approverId = firstNonNull(finalApproverEmployeeId, app.getFinalApprovedByEmployeeId(),
                app.getFinanceApprovedByEmployeeId());
        app.setFinalApprovedAt(approvedAt);
        app.setFinalApprovedByEmployeeId(approverId);

        if (method == LoanDisbursementMethod.PAYROLL_PERIOD) {
            scheduleInPayroll(app, approvedAt, approverId);
        } else {
            holdForExternalPayment(app, approvedAt, approverId);
        }
    }

    /** OUTSIDE_PAYROLL: queue for Finance. No account, no schedule, no deductions. */
    private void holdForExternalPayment(EmployeeLoanApplication app, LocalDateTime approvedAt, Long approverId) {
        LoanDisbursement d = newDisbursement(app, LoanDisbursementMethod.OUTSIDE_PAYROLL,
                LoanDisbursementStatus.PENDING_EXTERNAL_PAYMENT, approvedAt, approverId);
        d.setBankNameSnapshot(app.getBankNameSnapshot());
        d.setAccountNameSnapshot(app.getAccountNameSnapshot());
        d.setAccountNumberSnapshot(app.getAccountNumberSnapshot());
        if (isBlank(d.getBankNameSnapshot()) || isBlank(d.getAccountNameSnapshot())
                || isBlank(d.getAccountNumberSnapshot())) {
            log.warn("Loan {} is outside-payroll but its bank snapshot is incomplete; Finance will need to confirm details.",
                    app.getApplicationNumber());
        }
        disbursements.save(d);

        LocalDateTime now = LocalDateTime.now();
        app.setStatus(LoanApplicationStatus.PENDING_DISBURSEMENT);
        app.setDisbursementPendingAt(now);
        applications.save(app);

        activityService.record(app.getId(), LoanActivityType.DISBURSEMENT_PENDING,
                "Final approval complete. Loan of " + app.getRequestedAmount().toPlainString()
                        + " is waiting for Finance to confirm external payment.", approverId);

        // After-commit and never throws: a mail problem cannot undo the approval.
        notifications.notifyExternalPaymentPendingEmployee(app.getId());
        notifications.notifyExternalPaymentPendingFinance(app.getId());
    }

    /** PAYROLL_PERIOD: activate now; the amount is paid as an earning in the current open payroll period. */
    private void scheduleInPayroll(EmployeeLoanApplication app, LocalDateTime approvedAt, Long approverId) {
        PayrollPeriod period = requireOpenPayrollPeriod(app.getEmployee());

        // The payroll period's pay month is the disbursement month (month of the period end).
        LocalDate disbursementDate = period.getPeriodEnd();
        LocalDate selected = firstOfMonth(app.getRepaymentStartMonth());
        LocalDate effective = calculateEffectiveRepaymentStart(selected, disbursementDate);

        LoanDisbursement d = newDisbursement(app, LoanDisbursementMethod.PAYROLL_PERIOD,
                LoanDisbursementStatus.SCHEDULED_IN_PAYROLL, approvedAt, approverId);
        d.setPayrollPeriodId(period.getId());
        d.setDisbursementMonth(YearMonth.from(disbursementDate).atDay(1));
        d.setEffectiveRepaymentStartMonth(effective);
        d = disbursements.save(d);

        EmployeeLoanAccount account = accountService.activate(app.getId(), effective);
        d.setLoanAccountId(account.getId());
        d.setEffectiveRepaymentStartMonth(account.getRepaymentStartMonth());
        disbursements.save(d);

        activityService.recordForAccount(app.getId(), account.getId(),
                LoanActivityType.PAYROLL_DISBURSEMENT_SCHEDULED,
                "Final approval complete. Loan amount " + app.getRequestedAmount().toPlainString()
                        + " scheduled for disbursement in payroll period " + period.getPeriodStart()
                        + " to " + period.getPeriodEnd() + ".", approverId);
        recordStartAdjustment(app, account.getId(), selected, account.getRepaymentStartMonth(),
                d.getDisbursementMonth(), approverId);

        notifications.notifyPayrollDisbursementScheduled(app.getId());

        // Recalculate this employee's current payroll so the disbursement line appears in the same period.
        schedulePayrollRecalculationAfterCommit(app.getEmployee().getId(), period.getPeriodEnd(), app.getId());
    }

    /**
     * Requests payroll recalculation only AFTER the surrounding transaction commits, so payroll calculation
     * sees the newly committed LoanDisbursement row. Never throws: a payroll messaging problem must not undo
     * the loan approval. Do not call the dispatcher directly before commit.
     */
    private void schedulePayrollRecalculationAfterCommit(Long employeeId, LocalDate effectiveDate,
                                                         Long loanApplicationId) {
        if (employeeId == null || effectiveDate == null) {
            log.warn("Loan payroll recalculation skipped for application {} - employeeId or effectiveDate is missing.",
                    loanApplicationId);
            return;
        }

        afterCommitExecutor.runAfterCommit(() -> {
            try {
                payrollMessageDispatcher.requestPayroll(employeeId, effectiveDate);
                log.info("Loan payroll recalculation requested for application {}, employee {}, effective date {}.",
                        loanApplicationId, employeeId, effectiveDate);
            } catch (Exception ex) {
                log.warn("Loan payroll recalculation request failed for application {}, employee {}, effective date {}: {}",
                        loanApplicationId, employeeId, effectiveDate, ex.getMessage(), ex);
            }
        });
    }

    /** Retry safety for a payroll loan whose row was saved but activation did not complete. */
    private void resumeIfIncomplete(EmployeeLoanApplication app, LoanDisbursement d) {
        boolean approved = app.getStatus() == LoanApplicationStatus.FINANCE_APPROVED
                || app.getStatus() == LoanApplicationStatus.CUSTOM_APPROVED;
        if (d.getMethod() == LoanDisbursementMethod.PAYROLL_PERIOD && d.getLoanAccountId() == null && approved) {
            LocalDate effective = d.getEffectiveRepaymentStartMonth() != null
                    ? d.getEffectiveRepaymentStartMonth()
                    : calculateEffectiveRepaymentStart(app.getRepaymentStartMonth(), LocalDate.now());
            EmployeeLoanAccount account = accountService.activate(app.getId(), effective);
            d.setLoanAccountId(account.getId());
            d.setEffectiveRepaymentStartMonth(account.getRepaymentStartMonth());
            disbursements.save(d);
            notifications.notifyPayrollDisbursementScheduled(app.getId());
        }
    }

    private PayrollPeriod requireOpenPayrollPeriod(Employee employee) {
        if (employee == null || employee.getDepartment() == null
                || employee.getDepartment().getCompany() == null) {
            throw new IllegalStateException(
                    "The employee has no department/company, so the current payroll period cannot be determined.");
        }
        Long companyId = employee.getDepartment().getCompany().getId();
        PayrollPeriod period = payrollPeriodService.getCurrentPeriod(companyId);
        if (period == null || period.getStatus() != PayrollPeriodStatus.OPEN) {
            throw new IllegalStateException(
                    "There is no open payroll period for this employee's company, so the loan cannot be disbursed "
                            + "inside payroll right now.");
        }
        return period;
    }

    private LoanDisbursement newDisbursement(EmployeeLoanApplication app, LoanDisbursementMethod method,
                                             LoanDisbursementStatus status, LocalDateTime approvedAt,
                                             Long approverId) {
        LoanDisbursement d = new LoanDisbursement();
        d.setLoanApplicationId(app.getId());
        d.setEmployeeId(app.getEmployee().getId());
        d.setLoanProductId(app.getLoanProduct().getId());
        d.setMethod(method);
        d.setStatus(status);
        d.setAmount(app.getRequestedAmount());
        d.setSelectedRepaymentStartMonth(firstOfMonth(app.getRepaymentStartMonth()));
        d.setFinalApprovedAt(approvedAt);
        d.setFinalApprovedByEmployeeId(approverId);
        return d;
    }

    // =====================================================================================
    // Finance confirms external payment
    // =====================================================================================

    @Override
    @Transactional
    public LoanDisbursementResponse confirmExternalPaid(Long disbursementId, LoanConfirmDisbursementCommand command) {
        if (!authenticationManager.isLoanFinanceApprover() && !authenticationManager.isAdmin()) {
            throw new AccessDeniedException("Only Finance or Admin can confirm loan payments.");
        }
        LoanConfirmDisbursementCommand cmd = command != null ? command : new LoanConfirmDisbursementCommand();
        Long actorId = cmd.getActorEmployeeId();
        if (actorId == null) {
            throw new IllegalArgumentException("The confirming Finance user could not be identified.");
        }

        LoanDisbursement d = disbursements.findByIdForUpdate(disbursementId)
                .orElseThrow(() -> new EntityNotFoundException("Disbursement not found: " + disbursementId));
        if (d.getMethod() != LoanDisbursementMethod.OUTSIDE_PAYROLL) {
            throw new IllegalStateException("Only outside-payroll disbursements are confirmed by Finance.");
        }
        if (d.getStatus() != LoanDisbursementStatus.PENDING_EXTERNAL_PAYMENT) {
            throw new IllegalStateException("This disbursement is already " + d.getStatus()
                    + " and cannot be confirmed again.");
        }
        final Long loanApplicationId = d.getLoanApplicationId(); // d is reassigned below, so the lambda can't capture it
        EmployeeLoanApplication app = applications.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Loan application not found: " + loanApplicationId));
        if (app.getStatus() != LoanApplicationStatus.PENDING_DISBURSEMENT) {
            throw new IllegalStateException("Loan " + app.getApplicationNumber()
                    + " is not pending disbursement (status " + app.getStatus() + ").");
        }

        LocalDate today = LocalDate.now();
        LocalDate paidDate = cmd.getPaidDate() != null ? cmd.getPaidDate() : today;
        if (paidDate.isAfter(today)) {
            throw new IllegalArgumentException("Paid date cannot be in the future.");
        }
        if (d.getFinalApprovedAt() != null && paidDate.isBefore(d.getFinalApprovedAt().toLocalDate())) {
            throw new IllegalArgumentException("Paid date cannot be before the loan's final approval date ("
                    + d.getFinalApprovedAt().toLocalDate() + ").");
        }
        String reference = trimToNull(cmd.getPaymentReference());
        String comment = trimToNull(cmd.getComment());
        if (reference != null && reference.length() > MAX_REFERENCE_LENGTH) {
            throw new IllegalArgumentException("Payment reference cannot exceed " + MAX_REFERENCE_LENGTH + " characters.");
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment cannot exceed " + MAX_COMMENT_LENGTH + " characters.");
        }

        LocalDate selected = firstOfMonth(app.getRepaymentStartMonth());
        LocalDate effective = calculateEffectiveRepaymentStart(selected, paidDate);
        LocalDateTime paidAt = paidDate.equals(today) ? LocalDateTime.now() : paidDate.atStartOfDay();

        d.setStatus(LoanDisbursementStatus.PAID);
        d.setPaidAt(paidAt);
        d.setPaidByEmployeeId(actorId);
        d.setPaymentReference(reference);
        d.setPaymentComment(comment);
        d.setDisbursementMonth(YearMonth.from(paidDate).atDay(1));
        d.setSelectedRepaymentStartMonth(selected);
        d.setEffectiveRepaymentStartMonth(effective);
        d = disbursements.save(d);

        // Activation creates the account + locked schedule from the effective month (idempotent).
        EmployeeLoanAccount account = accountService.activate(app.getId(), effective);
        d.setLoanAccountId(account.getId());
        d.setEffectiveRepaymentStartMonth(account.getRepaymentStartMonth());
        d = disbursements.save(d);

        activityService.recordForAccount(app.getId(), account.getId(),
                LoanActivityType.EXTERNAL_DISBURSEMENT_CONFIRMED,
                "Finance confirmed payment of " + d.getAmount().toPlainString() + " on " + paidDate
                        + (reference != null ? " (reference " + reference + ")" : "")
                        + ". Loan is now active; first deduction month " + YearMonth.from(account.getRepaymentStartMonth()) + ".",
                actorId);
        recordStartAdjustment(app, account.getId(), selected, account.getRepaymentStartMonth(),
                d.getDisbursementMonth(), actorId);

        notifications.notifyExternalPaymentConfirmed(app.getId());

        return toResponses(List.of(d)).get(0);
    }

    private void recordStartAdjustment(EmployeeLoanApplication app, Long accountId, LocalDate selected,
                                       LocalDate effective, LocalDate disbursementMonth, Long actorId) {
        if (effective != null && selected != null && !effective.equals(selected)) {
            activityService.recordForAccount(app.getId(), accountId, LoanActivityType.REPAYMENT_START_ADJUSTED,
                    "Repayment start moved from " + YearMonth.from(selected) + " to " + YearMonth.from(effective)
                            + " because the loan was disbursed in " + YearMonth.from(disbursementMonth)
                            + " and repayment cannot start in the disbursement month.", actorId);
        }
    }

    // =====================================================================================
    // Queries
    // =====================================================================================

    @Override
    public List<LoanDisbursementResponse> listForFinance(LoanDisbursementFilter filter) {
        return loadFiltered(filter);
    }

    @Override
    public LoanDisbursementResponse getById(Long disbursementId) {
        LoanDisbursement d = disbursements.findById(disbursementId)
                .orElseThrow(() -> new EntityNotFoundException("Disbursement not found: " + disbursementId));
        return toResponses(List.of(d)).get(0);
    }

    private List<LoanDisbursementResponse> loadFiltered(LoanDisbursementFilter filter) {
        LoanDisbursementFilter f = filter != null ? filter : new LoanDisbursementFilter();
        List<LoanDisbursement> rows = disbursements.findAll(toSpecification(f));
        List<LoanDisbursementResponse> responses = toResponses(rows);

        // Name/number/department live on Employee (disbursement stores plain ids), so they are applied here.
        String search = normalize(f.getSearch());
        String dept = normalize(f.getDepartment());
        if (search != null || dept != null) {
            responses = responses.stream()
                    .filter(r -> search == null
                            || contains(r.getEmployeeName(), search)
                            || contains(r.getEmployeeNumber(), search)
                            || contains(r.getApplicationNumber(), search))
                    .filter(r -> dept == null || contains(r.getDepartment(), dept))
                    .toList();
        }

        boolean pendingView = f.getStatus() == LoanDisbursementStatus.PENDING_EXTERNAL_PAYMENT;
        Comparator<LoanDisbursementResponse> byApproval = Comparator.comparing(
                LoanDisbursementResponse::getFinalApprovedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        if (pendingView) {
            return responses.stream().sorted(byApproval.thenComparing(LoanDisbursementResponse::getId)).toList();
        }
        Comparator<LoanDisbursementResponse> newestFirst = Comparator
                .comparing((LoanDisbursementResponse r) -> r.getPaidAt() != null ? r.getPaidAt() : r.getFinalApprovedAt(),
                        Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                .reversed()
                .thenComparing(LoanDisbursementResponse::getId, Comparator.reverseOrder());
        return responses.stream().sorted(newestFirst).toList();
    }

    private Specification<LoanDisbursement> toSpecification(LoanDisbursementFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.getStatus() != null) p.add(cb.equal(root.get("status"), f.getStatus()));
            if (f.getMethod() != null) p.add(cb.equal(root.get("method"), f.getMethod()));
            if (f.getLoanProductId() != null) p.add(cb.equal(root.get("loanProductId"), f.getLoanProductId()));
            if (f.getFromFinalApprovedAt() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("finalApprovedAt"), f.getFromFinalApprovedAt().atStartOfDay()));
            }
            if (f.getToFinalApprovedAt() != null) {
                p.add(cb.lessThan(root.get("finalApprovedAt"), f.getToFinalApprovedAt().plusDays(1).atStartOfDay()));
            }
            if (f.getFromPaidAt() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("paidAt"), f.getFromPaidAt().atStartOfDay()));
            }
            if (f.getToPaidAt() != null) {
                p.add(cb.lessThan(root.get("paidAt"), f.getToPaidAt().plusDays(1).atStartOfDay()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    // =====================================================================================
    // CSV
    // =====================================================================================

    @Override
    public byte[] exportCsvForFinance(LoanDisbursementFilter filter) {
        List<LoanDisbursementResponse> rows = loadFiltered(filter); // every matching row, no paging

        StringBuilder csv = new StringBuilder("\uFEFF"); // BOM so Excel reads UTF-8 correctly
        appendRow(csv, CSV_HEADER);
        for (LoanDisbursementResponse r : rows) {
            appendRow(csv,
                    r.getApplicationNumber(), r.getEmployeeNumber(), r.getEmployeeName(), r.getDepartment(),
                    r.getLoanProductName(),
                    r.getAmount() != null ? r.getAmount().toPlainString() : "",
                    r.getBankName(), r.getAccountName(), r.getAccountNumber(), r.getApprovalRoute(),
                    str(r.getFinalApprovedAt()),
                    r.getSelectedRepaymentStartMonth() != null ? YearMonth.from(r.getSelectedRepaymentStartMonth()).toString() : "",
                    r.getEffectiveRepaymentStartMonth() != null ? YearMonth.from(r.getEffectiveRepaymentStartMonth()).toString() : "",
                    r.getStatusLabel(), str(r.getPaidAt()), r.getPaidByName(),
                    r.getPaymentReference(), r.getPaymentComment());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(cells[i]));
        }
        sb.append("\r\n");
    }

    /** RFC 4180 quoting plus a guard against spreadsheet formula injection from free-text fields. */
    static String escapeCsv(String value) {
        if (value == null || value.isEmpty()) return "";
        String v = value;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // =====================================================================================
    // Mapping
    // =====================================================================================

    private List<LoanDisbursementResponse> toResponses(List<LoanDisbursement> rows) {
        if (rows.isEmpty()) return List.of();

        Map<Long, EmployeeLoanApplication> apps = applications.findAllById(
                        rows.stream().map(LoanDisbursement::getLoanApplicationId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(EmployeeLoanApplication::getId, Function.identity()));

        Set<Long> employeeIds = rows.stream().map(LoanDisbursement::getEmployeeId).collect(Collectors.toSet());
        rows.stream().map(LoanDisbursement::getPaidByEmployeeId).filter(Objects::nonNull).forEach(employeeIds::add);
        Map<Long, Employee> employees = employeeRepository.findAllById(employeeIds)
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));

        return rows.stream().map(d -> {
            EmployeeLoanApplication app = apps.get(d.getLoanApplicationId());
            Employee emp = employees.get(d.getEmployeeId());
            Employee paidBy = d.getPaidByEmployeeId() != null ? employees.get(d.getPaidByEmployeeId()) : null;
            LocalDate selected = d.getSelectedRepaymentStartMonth();
            LocalDate effective = d.getEffectiveRepaymentStartMonth();

            return LoanDisbursementResponse.builder()
                    .id(d.getId())
                    .loanApplicationId(d.getLoanApplicationId())
                    .applicationNumber(app != null ? app.getApplicationNumber() : null)
                    .loanAccountId(d.getLoanAccountId())
                    .employeeId(d.getEmployeeId())
                    .employeeNumber(emp != null ? emp.getEmployeeNumber() : null)
                    .employeeName(emp != null ? emp.getFullName() : null)
                    .department(emp != null && emp.getDepartment() != null ? emp.getDepartment().getName() : null)
                    .loanProductId(d.getLoanProductId())
                    .loanProductName(app != null && app.getLoanProduct() != null ? app.getLoanProduct().getName() : null)
                    .method(d.getMethod())
                    .methodLabel(methodLabel(d.getMethod()))
                    .status(d.getStatus())
                    .statusLabel(statusLabel(d.getStatus()))
                    .amount(d.getAmount())
                    .bankName(d.getBankNameSnapshot())
                    .accountName(d.getAccountNameSnapshot())
                    .accountNumber(d.getAccountNumberSnapshot())
                    .approvalRoute(app != null && app.getApprovalRouteTypeSnapshot() != null
                            ? routeLabel(app) : null)
                    .finalApprovedAt(d.getFinalApprovedAt())
                    .finalApprovedByEmployeeId(d.getFinalApprovedByEmployeeId())
                    .selectedRepaymentStartMonth(selected)
                    .effectiveRepaymentStartMonth(effective)
                    .disbursementMonth(d.getDisbursementMonth())
                    .repaymentStartAdjusted(effective != null && selected != null && !effective.equals(selected))
                    .payrollPeriodId(d.getPayrollPeriodId())
                    .payrollRunId(d.getPayrollRunId())
                    .payrollLineItemId(d.getPayrollLineItemId())
                    .paidAt(d.getPaidAt())
                    .paidByEmployeeId(d.getPaidByEmployeeId())
                    .paidByName(paidBy != null ? paidBy.getFullName() : null)
                    .paymentReference(d.getPaymentReference())
                    .paymentComment(d.getPaymentComment())
                    .build();
        }).toList();
    }

    private static String routeLabel(EmployeeLoanApplication app) {
        String name = app.getCustomApprovalPathNameSnapshot();
        return name != null && !name.isBlank()
                ? app.getApprovalRouteTypeSnapshot().name() + " - " + name
                : app.getApprovalRouteTypeSnapshot().name();
    }

    static String methodLabel(LoanDisbursementMethod m) {
        if (m == null) return null;
        return switch (m) {
            case PAYROLL_PERIOD -> "Pay inside payroll period";
            case OUTSIDE_PAYROLL -> "Pay outside payroll system";
        };
    }

    static String statusLabel(LoanDisbursementStatus s) {
        if (s == null) return null;
        return switch (s) {
            case SCHEDULED_IN_PAYROLL -> "Scheduled in payroll";
            case PENDING_EXTERNAL_PAYMENT -> "Pending external payment";
            case PAID -> "Paid";
            case CANCELLED -> "Cancelled";
            case FAILED -> "Failed";
        };
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private static LocalDate firstOfMonth(LocalDate d) {
        return d == null ? null : d.withDayOfMonth(1);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalize(String s) {
        String t = trimToNull(s);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}