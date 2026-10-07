package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.loan.dto.EmployeeLoanExposureResponse;
import com.justjava.humanresource.loan.dto.FinanceLoanDashboardResponse;
import com.justjava.humanresource.loan.dto.HrLoanDashboardResponse;
import com.justjava.humanresource.loan.dto.LoanApplicationSummaryResponse;
import com.justjava.humanresource.loan.dto.LoanMissedDeductionResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanAccount;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.entity.LoanProduct;
import com.justjava.humanresource.loan.entity.LoanRepaymentSchedule;
import com.justjava.humanresource.loan.enums.LoanAccountStatus;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanAccountRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApprovalStepRepository;
import com.justjava.humanresource.loan.repository.LoanDisbursementRepository;
import com.justjava.humanresource.loan.repository.LoanProductRepository;
import com.justjava.humanresource.loan.repository.LoanRepaymentScheduleRepository;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService;
import com.justjava.humanresource.loan.service.LoanReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanReportServiceImpl implements LoanReportService {

    /** What Finance monitors: its own queue and anything that reaches payroll. */
    private static final Set<LoanApplicationStatus> FINANCE_VISIBLE = EnumSet.of(
            LoanApplicationStatus.HR_APPROVED,
            LoanApplicationStatus.PENDING_FINANCE_APPROVAL,
            LoanApplicationStatus.FINANCE_APPROVED,
            LoanApplicationStatus.CUSTOM_APPROVED,
            LoanApplicationStatus.PENDING_DISBURSEMENT,
            LoanApplicationStatus.ACTIVE,
            LoanApplicationStatus.COMPLETED,
            LoanApplicationStatus.CLOSED);

    private static final int PAYROLL_IMPACT_MONTHS = 6;
    private static final int TOP_EXPOSURES = 5;

    private final AuthenticationManager authenticationManager;
    private final EmployeeLoanApplicationRepository applications;
    private final EmployeeLoanApprovalStepRepository steps;
    private final EmployeeLoanAccountRepository accounts;
    private final LoanRepaymentScheduleRepository schedules;
    private final LoanProductRepository products;
    private final LoanEmployeeContextService contextService;
    private final LoanDisbursementRepository disbursements;

    // ------------------------------------------------------------------ lists

    @Override
    public List<LoanApplicationSummaryResponse> listApplicationsForHr(LoanApplicationStatus status) {
        requireHr();
        return summarize(applications.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> a.getStatus() != LoanApplicationStatus.DRAFT)
                .filter(a -> status == null || a.getStatus() == status)
                .toList());
    }

    @Override
    public List<LoanApplicationSummaryResponse> listApplicationsForFinance(LoanApplicationStatus status) {
        requireFinance();
        return summarize(applications.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> FINANCE_VISIBLE.contains(a.getStatus()))
                .filter(a -> status == null || a.getStatus() == status)
                .toList());
    }

    @Override
    public List<LoanMissedDeductionResponse> listMissedDeductions() {
        if (!(authenticationManager.isHumanResource() || authenticationManager.isFinancialOfficer()
                || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("HR or Finance access is required.");
        }
        List<LoanRepaymentSchedule> rows = schedules.findByStatusOrderByDueMonthDesc(LoanRepaymentStatus.MISSED);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, EmployeeLoanAccount> accountById = accounts
                .findAllById(rows.stream().map(LoanRepaymentSchedule::getLoanAccountId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(EmployeeLoanAccount::getId, a -> a));
        Map<Long, EmployeeLoanApplication> appById = applications
                .findAllById(accountById.values().stream().map(EmployeeLoanAccount::getLoanApplicationId)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(EmployeeLoanApplication::getId, a -> a));

        List<LoanMissedDeductionResponse> result = new ArrayList<>();
        for (LoanRepaymentSchedule s : rows) {
            EmployeeLoanAccount account = accountById.get(s.getLoanAccountId());
            EmployeeLoanApplication app = account == null ? null : appById.get(account.getLoanApplicationId());
            if (account == null || app == null) {
                continue;
            }
            Employee e = app.getEmployee();
            result.add(LoanMissedDeductionResponse.builder()
                    .repaymentScheduleId(s.getId())
                    .loanAccountId(account.getId())
                    .loanApplicationId(app.getId())
                    .applicationNumber(app.getApplicationNumber())
                    .employeeId(e.getId())
                    .employeeName(e.getFullName())
                    .departmentName(e.getDepartment() == null ? null : e.getDepartment().getName())
                    .loanProductName(app.getLoanProduct().getName())
                    .sequenceNumber(s.getSequenceNumber())
                    .dueMonth(s.getDueMonth())
                    .expectedAmount(s.getExpectedAmount())
                    .paidAmount(s.getPaidAmount())
                    .outstandingAmount(s.getOutstandingAmount())
                    .status(s.getStatus())
                    .payrollRunId(s.getPayrollRunId())
                    .missedAt(s.getMissedAt())
                    .loanOutstandingBalance(account.getOutstandingBalance())
                    .build());
        }
        return result;
    }

    // ------------------------------------------------------------------ dashboards

    @Override
    public HrLoanDashboardResponse getHrDashboard() {
        requireHr();
        List<EmployeeLoanApplication> apps = applications.findAllByOrderByCreatedAtDesc();
        List<EmployeeLoanAccount> all = accounts.findAllByOrderByCreatedAtDesc();
        List<EmployeeLoanAccount> active = all.stream().filter(a -> a.getStatus() == LoanAccountStatus.ACTIVE).toList();
        List<LoanRepaymentSchedule> missed = schedules.findByStatusOrderByDueMonthDesc(LoanRepaymentStatus.MISSED);

        Map<LoanApplicationStatus, Long> byStatus = countByStatus(apps);

        // Product usage: any application (draft included) counts as use of the product.
        Map<Long, List<EmployeeLoanApplication>> appsByProduct =
                apps.stream().collect(Collectors.groupingBy(a -> a.getLoanProduct().getId()));
        Map<Long, List<EmployeeLoanAccount>> activeByProduct =
                active.stream().collect(Collectors.groupingBy(EmployeeLoanAccount::getLoanProductId));
        List<HrLoanDashboardResponse.ProductUsage> usage = products.findAllByOrderByNameAsc().stream()
                .map(p -> HrLoanDashboardResponse.ProductUsage.builder()
                        .loanProductId(p.getId())
                        .loanProductName(p.getName())
                        .applicationCount(appsByProduct.getOrDefault(p.getId(), List.of()).size())
                        .activeLoanCount(activeByProduct.getOrDefault(p.getId(), List.of()).size())
                        .outstandingBalance(sumBalance(activeByProduct.getOrDefault(p.getId(), List.of())))
                        .build())
                .toList();

        return HrLoanDashboardResponse.builder()
                .totalProducts(products.count())
                .activeProducts(products.findByActiveTrueOrderByNameAsc().size())
                .pendingHrApprovalCount(byStatus.getOrDefault(LoanApplicationStatus.PENDING_HR_APPROVAL, 0L))
                .pendingCustomApprovalCount(byStatus.getOrDefault(LoanApplicationStatus.PENDING_CUSTOM_APPROVAL, 0L))
                .pendingFinanceApprovalCount(byStatus.getOrDefault(LoanApplicationStatus.PENDING_FINANCE_APPROVAL, 0L))
                .returnedCount(byStatus.getOrDefault(LoanApplicationStatus.RETURNED_FOR_CORRECTION, 0L))
                .rejectedCount(byStatus.getOrDefault(LoanApplicationStatus.REJECTED, 0L))
                .pendingDisbursementCount(pendingDisbursementCount())
                .pendingDisbursementAmount(pendingDisbursementAmount())
                .activeLoanCount(active.size())
                .completedLoanCount(all.stream().filter(a -> a.getStatus() == LoanAccountStatus.COMPLETED).count())
                .totalOutstandingBalance(sumBalance(active))
                .totalMonthlyDeduction(active.stream().map(a -> nz(a.getRepaymentAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .missedDeductionCount(missed.size())
                .missedDeductionAmount(missed.stream().map(r -> nz(r.getOutstandingAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .applicationsByStatus(byStatus)
                .productUsage(usage)
                .topExposures(topExposures(active))
                .build();
    }

    @Override
    public FinanceLoanDashboardResponse getFinanceDashboard() {
        requireFinance();
        List<EmployeeLoanApplication> apps = applications.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> FINANCE_VISIBLE.contains(a.getStatus())).toList();
        List<EmployeeLoanAccount> all = accounts.findAllByOrderByCreatedAtDesc();
        List<EmployeeLoanAccount> active = all.stream().filter(a -> a.getStatus() == LoanAccountStatus.ACTIVE).toList();
        List<LoanRepaymentSchedule> missed = schedules.findByStatusOrderByDueMonthDesc(LoanRepaymentStatus.MISSED);

        // Interest still owed: each open installment's interest share, in proportion to what is outstanding on it.
        BigDecimal interest = BigDecimal.ZERO;
        for (EmployeeLoanAccount a : active) {
            for (LoanRepaymentSchedule row : schedules.findByLoanAccountIdOrderBySequenceNumberAsc(a.getId())) {
                BigDecimal owed = nz(row.getOutstandingAmount());
                if (owed.signum() > 0 && nz(row.getExpectedAmount()).signum() > 0) {
                    interest = interest.add(nz(row.getInterestPortion()).multiply(owed)
                            .divide(row.getExpectedAmount(), 2, RoundingMode.HALF_UP));
                }
            }
        }
        BigDecimal outstanding = sumBalance(active);

        List<EmployeeLoanAccount> activated = all.stream()
                .filter(a -> a.getStatus() == LoanAccountStatus.ACTIVE || a.getStatus() == LoanAccountStatus.COMPLETED)
                .toList();

        return FinanceLoanDashboardResponse.builder()
                .pendingFinanceApprovalCount(apps.stream()
                        .filter(a -> a.getStatus() == LoanApplicationStatus.PENDING_FINANCE_APPROVAL).count())
                .activeLoanCount(active.size())
                .completedLoanCount(all.stream().filter(a -> a.getStatus() == LoanAccountStatus.COMPLETED).count())
                .totalPrincipalApproved(activated.stream().map(a -> nz(a.getPrincipalAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .totalOutstandingBalance(outstanding)
                .outstandingPrincipal(outstanding.subtract(interest))
                .outstandingInterest(interest)
                .totalRepaid(activated.stream().map(a -> nz(a.getTotalPaidAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .missedDeductionCount(missed.size())
                .missedDeductionAmount(missed.stream().map(r -> nz(r.getOutstandingAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .pendingDisbursementCount(pendingDisbursementCount())
                .pendingDisbursementAmount(pendingDisbursementAmount())
                .paidDisbursementAmountThisMonth(paidDisbursementAmountThisMonth())
                .applicationsByStatus(countByStatus(apps))
                .payrollImpact(payrollImpact(active))
                .topExposures(topExposures(active))
                .build();
    }

    // Pending disbursements are approved but not paid, so they have no loan account: they are counted here
    // and are deliberately left out of "active" figures and the payroll deduction impact below.

    private long pendingDisbursementCount() {
        return disbursements.countByMethodAndStatus(
                LoanDisbursementMethod.OUTSIDE_PAYROLL, LoanDisbursementStatus.PENDING_EXTERNAL_PAYMENT);
    }

    private BigDecimal pendingDisbursementAmount() {
        return nz(disbursements.sumAmountByMethodAndStatus(
                LoanDisbursementMethod.OUTSIDE_PAYROLL, LoanDisbursementStatus.PENDING_EXTERNAL_PAYMENT));
    }

    private BigDecimal paidDisbursementAmountThisMonth() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        return nz(disbursements.sumAmountPaidBetween(
                LoanDisbursementMethod.OUTSIDE_PAYROLL, LoanDisbursementStatus.PAID,
                first.atStartOfDay(), first.plusMonths(1).atStartOfDay()));
    }

    private List<FinanceLoanDashboardResponse.PayrollImpact> payrollImpact(List<EmployeeLoanAccount> active) {
        Map<Long, EmployeeLoanAccount> activeById =
                active.stream().collect(Collectors.toMap(EmployeeLoanAccount::getId, a -> a));
        List<FinanceLoanDashboardResponse.PayrollImpact> impact = new ArrayList<>();
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        for (int i = 0; i < PAYROLL_IMPACT_MONTHS; i++) {
            LocalDate month = first.plusMonths(i);
            List<LoanRepaymentSchedule> rows = schedules.findByDueMonthAndStatusIn(month,
                            List.of(LoanRepaymentStatus.PENDING, LoanRepaymentStatus.PARTIALLY_PAID)).stream()
                    .filter(r -> activeById.containsKey(r.getLoanAccountId()))
                    .toList();
            Set<Long> employees = rows.stream().map(r -> activeById.get(r.getLoanAccountId()).getEmployeeId())
                    .collect(Collectors.toSet());
            impact.add(FinanceLoanDashboardResponse.PayrollImpact.builder()
                    .month(month)
                    .expectedDeductionAmount(rows.stream().map(r -> nz(r.getOutstandingAmount()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .loanCount(rows.stream().map(LoanRepaymentSchedule::getLoanAccountId).distinct().count())
                    .employeeCount(employees.size())
                    .build());
        }
        return impact;
    }

    private List<EmployeeLoanExposureResponse> topExposures(List<EmployeeLoanAccount> active) {
        Map<Long, BigDecimal> byEmployee = new HashMap<>();
        for (EmployeeLoanAccount a : active) {
            byEmployee.merge(a.getEmployeeId(), nz(a.getOutstandingBalance()), BigDecimal::add);
        }
        return byEmployee.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(TOP_EXPOSURES)
                .map(e -> contextService.getExposure(e.getKey(), null))
                .toList();
    }

    // ------------------------------------------------------------------ summary rows

    private List<LoanApplicationSummaryResponse> summarize(List<EmployeeLoanApplication> apps) {
        if (apps.isEmpty()) {
            return List.of();
        }
        Map<Long, EmployeeLoanApprovalStep> pendingStep = new HashMap<>();
        for (EmployeeLoanApplication a : apps) {
            if (LoanApplicationSupport.IN_APPROVAL.contains(a.getStatus())) {
                Optional<EmployeeLoanApprovalStep> cur = LoanApplicationSupport.currentPending(
                        steps.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(a.getId()));
                cur.ifPresent(s -> pendingStep.put(a.getId(), s));
            }
        }
        Map<Long, String> names = contextService.employeeNames(pendingStep.values().stream()
                .map(EmployeeLoanApprovalStep::getApproverEmployeeId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));

        Map<Long, EmployeeLoanAccount> accountByApp = accounts.findAllByOrderByCreatedAtDesc().stream()
                .collect(Collectors.toMap(EmployeeLoanAccount::getLoanApplicationId, a -> a, (x, y) -> x));
        Set<Long> accountsWithMissed = accountByApp.isEmpty() ? Set.of()
                : schedules.findByLoanAccountIdInAndStatus(
                        accountByApp.values().stream().map(EmployeeLoanAccount::getId).toList(),
                        LoanRepaymentStatus.MISSED).stream()
                .map(LoanRepaymentSchedule::getLoanAccountId).collect(Collectors.toCollection(HashSet::new));

        List<LoanApplicationSummaryResponse> rows = new ArrayList<>();
        for (EmployeeLoanApplication a : apps) {
            LoanProduct p = a.getLoanProduct();
            Employee e = a.getEmployee();
            LoanApprovalRouteType routeType = a.getApprovalRouteTypeSnapshot() != null
                    ? a.getApprovalRouteTypeSnapshot() : p.getApprovalRouteType();
            EmployeeLoanApprovalStep cur = pendingStep.get(a.getId());
            EmployeeLoanAccount account = accountByApp.get(a.getId());

            rows.add(LoanApplicationSummaryResponse.builder()
                    .id(a.getId())
                    .applicationNumber(a.getApplicationNumber())
                    .status(a.getStatus())
                    .employeeId(e.getId())
                    .employeeName(e.getFullName())
                    .departmentName(e.getDepartment() == null ? null : e.getDepartment().getName())
                    .loanProductId(p.getId())
                    .loanProductName(p.getName())
                    .requestedAmount(a.getRequestedAmount())
                    .repaymentAmount(a.getRepaymentAmount())
                    .tenorMonths(a.getTenorMonths())
                    .repaymentStartMonth(a.getRepaymentStartMonth())
                    .approvalRouteType(routeType)
                    .approvalRouteLabel(LoanApplicationSupport.routeLabel(routeType))
                    .currentApprovalOwner(cur == null ? null : ownerLabel(cur, names))
                    .currentApproverEmployeeId(cur == null ? null : cur.getApproverEmployeeId())
                    .loanAccountId(account == null ? null : account.getId())
                    .outstandingBalance(account == null ? null : account.getOutstandingBalance())
                    .hasMissedDeductions(account != null && accountsWithMissed.contains(account.getId()))
                    .submittedAt(a.getSubmittedAt())
                    .activatedAt(a.getActivatedAt())
                    .createdAt(a.getCreatedAt())
                    .updatedAt(a.getUpdatedAt())
                    .build());
        }
        return rows;
    }

    // ------------------------------------------------------------------ helpers

    private static String ownerLabel(EmployeeLoanApprovalStep s, Map<Long, String> names) {
        if (s.getApproverEmployeeId() != null && names.get(s.getApproverEmployeeId()) != null) {
            return names.get(s.getApproverEmployeeId());
        }
        return switch (s.getApprovalStage()) {
            case HR -> "HR group";
            case FINANCE -> "Finance group";
            case CUSTOM -> s.getApproverGroup() != null ? s.getApproverGroup() : "Custom approver";
        };
    }

    /** Counts per status in enum order, drafts left out (other employees' drafts are private). */
    private static Map<LoanApplicationStatus, Long> countByStatus(List<EmployeeLoanApplication> apps) {
        Map<LoanApplicationStatus, Long> counts = apps.stream()
                .collect(Collectors.groupingBy(EmployeeLoanApplication::getStatus, Collectors.counting()));
        Map<LoanApplicationStatus, Long> ordered = new LinkedHashMap<>();
        for (LoanApplicationStatus s : LoanApplicationStatus.values()) {
            if (s != LoanApplicationStatus.DRAFT && counts.containsKey(s)) {
                ordered.put(s, counts.get(s));
            }
        }
        return ordered;
    }

    private static BigDecimal sumBalance(List<EmployeeLoanAccount> list) {
        return list.stream().map(a -> nz(a.getOutstandingBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void requireHr() {
        if (!(authenticationManager.isHumanResource() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("HR access is required.");
        }
    }

    private void requireFinance() {
        if (!(authenticationManager.isFinancialOfficer() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("Finance access is required.");
        }
    }
}