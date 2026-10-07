package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.loan.dto.EmployeeLoanExposureResponse;
import com.justjava.humanresource.loan.dto.EmployeeLoanExposureResponse.ActiveLoanLine;
import com.justjava.humanresource.loan.dto.EmployeeLoanExposureResponse.PendingLoanLine;
import com.justjava.humanresource.loan.dto.LoanApprovalContextResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanAccount;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;
import com.justjava.humanresource.loan.entity.LoanProduct;
import com.justjava.humanresource.loan.enums.LoanAccountStatus;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanAccountRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApprovalStepRepository;
import com.justjava.humanresource.loan.repository.LoanProductRepository;
import com.justjava.humanresource.loan.repository.LoanRepaymentScheduleRepository;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanEmployeeContextServiceImpl implements LoanEmployeeContextService {

    private final AuthenticationManager authenticationManager;
    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeLoanApplicationRepository applicationRepository;
    private final EmployeeLoanApprovalStepRepository stepRepository;
    private final EmployeeLoanAccountRepository accountRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanProductRepository productRepository;

    // ------------------------------------------------------------ current user / snapshot

    @Override
    public Employee getCurrentEmployee() {
        String email = authenticationManager.getCurrentUserEmail();
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("No authenticated user.");
        }
        return findCurrentEmployee().orElseThrow(() ->
                new AccessDeniedException("No employee record is linked to " + email + "."));
    }

    @Override
    public Optional<Employee> findCurrentEmployee() {
        String email = authenticationManager.getCurrentUserEmail();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        // Repository lookup on purpose: EmployeeService.getByEmail throws when nothing matches, and an
        // exception crossing its @Transactional proxy would mark this whole request rollback-only.
        return employeeRepository.findByEmail(email.trim());
    }

    @Override
    public EmployeeSnapshot snapshot(Employee employee) {
        JobStep step = employee.getJobStep();
        Department department = employee.getDepartment();
        BigDecimal gross = step != null && step.getGrossSalary() != null
                ? step.getGrossSalary().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new EmployeeSnapshot(
                department == null ? null : department.getId(),
                department == null ? null : department.getName(),
                step == null || step.getJobGrade() == null ? null : step.getJobGrade().getName(),
                step == null ? null : step.getName(),
                gross);
    }

    // ------------------------------------------------------------ exposure

    @Override
    public EmployeeLoanExposureResponse getExposure(Long employeeId, Long excludeApplicationId) {
        List<EmployeeLoanAccount> accounts = accountRepository
                .findByEmployeeIdAndStatus(employeeId, LoanAccountStatus.ACTIVE).stream()
                .filter(a -> !Objects.equals(a.getLoanApplicationId(), excludeApplicationId))
                .toList();

        Map<Long, String> applicationNumbers = applicationRepository
                .findAllById(accounts.stream().map(EmployeeLoanAccount::getLoanApplicationId).toList()).stream()
                .collect(Collectors.toMap(EmployeeLoanApplication::getId,
                        EmployeeLoanApplication::getApplicationNumber));
        Map<Long, String> productNames = productRepository
                .findAllById(accounts.stream().map(EmployeeLoanAccount::getLoanProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(LoanProduct::getId, LoanProduct::getName));

        List<ActiveLoanLine> activeLines = new ArrayList<>();
        int missedTotal = 0;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal monthly = BigDecimal.ZERO;
        for (EmployeeLoanAccount a : accounts) {
            int missed = scheduleRepository.findByLoanAccountIdAndStatus(a.getId(), LoanRepaymentStatus.MISSED).size();
            missedTotal += missed;
            outstanding = outstanding.add(nz(a.getOutstandingBalance()));
            monthly = monthly.add(nz(a.getRepaymentAmount()));
            activeLines.add(ActiveLoanLine.builder()
                    .loanAccountId(a.getId())
                    .loanApplicationId(a.getLoanApplicationId())
                    .applicationNumber(applicationNumbers.get(a.getLoanApplicationId()))
                    .loanProductName(productNames.get(a.getLoanProductId()))
                    .principalAmount(a.getPrincipalAmount())
                    .totalRepayableAmount(a.getTotalRepayableAmount())
                    .totalPaidAmount(a.getTotalPaidAmount())
                    .outstandingBalance(a.getOutstandingBalance())
                    .repaymentAmount(a.getRepaymentAmount())
                    .tenorMonths(a.getTenorMonths())
                    .repaymentStartMonth(a.getRepaymentStartMonth())
                    .status(a.getStatus())
                    .missedDeductionCount(missed)
                    .build());
        }

        // Applications already turned into an account are counted above, so only look at in-approval ones.
        List<PendingLoanLine> pendingLines = applicationRepository
                .findByEmployeeIdAndStatusIn(employeeId, LoanApplicationSupport.IN_APPROVAL).stream()
                .filter(a -> !Objects.equals(a.getId(), excludeApplicationId))
                .map(a -> PendingLoanLine.builder()
                        .loanApplicationId(a.getId())
                        .applicationNumber(a.getApplicationNumber())
                        .loanProductName(a.getLoanProduct().getName())
                        .requestedAmount(a.getRequestedAmount())
                        .repaymentAmount(a.getRepaymentAmount())
                        .tenorMonths(a.getTenorMonths())
                        .status(a.getStatus())
                        .build())
                .toList();

        return EmployeeLoanExposureResponse.builder()
                .employeeId(employeeId)
                .employeeName(employeeRepository.findById(employeeId).map(Employee::getFullName).orElse(null))
                .activeLoanCount(activeLines.size())
                .pendingApplicationCount(pendingLines.size())
                .missedDeductionCount(missedTotal)
                .totalOutstandingBalance(outstanding)
                .totalMonthlyDeduction(monthly)
                .hasExistingExposure(!activeLines.isEmpty() || !pendingLines.isEmpty())
                .activeLoans(activeLines)
                .pendingApplications(pendingLines)
                .build();
    }

    @Override
    public LoanApprovalContextResponse buildApprovalContext(EmployeeLoanApplication application) {
        Employee employee = application.getEmployee();
        EmployeeSnapshot current = snapshot(employee);
        EmployeeLoanExposureResponse exposure = getExposure(employee.getId(), application.getId());

        BigDecimal gross = current.grossSalary();
        BigDecimal repayment = nz(application.getRepaymentAmount());
        BigDecimal percent = gross.signum() > 0
                ? repayment.multiply(BigDecimal.valueOf(100)).divide(gross, 2, RoundingMode.HALF_UP)
                : null;

        List<String> warnings = new ArrayList<>();
        if (gross.signum() <= 0) {
            warnings.add("No gross salary is recorded for this employee.");
        } else if (application.getGrossSalarySnapshot() != null
                && application.getGrossSalarySnapshot().compareTo(gross) != 0) {
            warnings.add("Gross salary has changed since the application was prepared.");
        }
        if (exposure.getActiveLoanCount() > 0) {
            warnings.add("Employee has " + exposure.getActiveLoanCount() + " active loan(s) with outstanding balance "
                    + exposure.getTotalOutstandingBalance().toPlainString() + ".");
        }
        if (exposure.getPendingApplicationCount() > 0) {
            warnings.add("Employee has " + exposure.getPendingApplicationCount()
                    + " other loan application(s) awaiting approval.");
        }
        if (exposure.getMissedDeductionCount() > 0) {
            warnings.add("Employee has " + exposure.getMissedDeductionCount()
                    + " missed loan deduction(s) on existing loans.");
        }

        return LoanApprovalContextResponse.builder()
                .employeeId(employee.getId())
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getFullName())
                .email(employee.getEmail())
                .departmentId(current.departmentId())
                .departmentName(current.departmentName())
                .jobTitle(null) // Employee has no job-title field; grade and step are provided instead
                .jobGradeName(current.jobGradeName())
                .jobStepName(current.jobStepName())
                .employmentStatus(employee.getEmploymentStatus() == null ? null : employee.getEmploymentStatus().name())
                .employmentDate(employee.getDateOfHire())
                .grossSalary(gross)
                .grossSalarySnapshot(application.getGrossSalarySnapshot())
                .repaymentToGrossPercent(percent)
                .projectedTotalMonthlyLoanDeduction(exposure.getTotalMonthlyDeduction().add(repayment))
                .exposure(exposure)
                .warnings(warnings)
                .build();
    }

    // ------------------------------------------------------------ access rules

    @Override
    public ViewerRole requireViewAccess(EmployeeLoanApplication application) {
        // An HR/Finance/admin login may have no Employee record; they can still view by role.
        Employee me = findCurrentEmployee().orElse(null);
        if (me != null && application.getEmployee().getId().equals(me.getId())) {
            return ViewerRole.EMPLOYEE;
        }
        if (application.getStatus() != LoanApplicationStatus.DRAFT) {
            if (authenticationManager.isHumanResource() || authenticationManager.isAdmin()) {
                return ViewerRole.HR;
            }
            if (authenticationManager.isFinancialOfficer()) {
                return ViewerRole.FINANCE;
            }
            if (me != null && isAssignedCustomApprover(application, me.getId())) {
                return ViewerRole.CUSTOM_APPROVER;
            }
        }
        throw new AccessDeniedException("You do not have access to this loan application.");
    }

    @Override
    public boolean isAssignedCustomApprover(EmployeeLoanApplication application, Long employeeId) {
        List<EmployeeLoanApprovalStep> steps =
                stepRepository.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(application.getId());
        Optional<EmployeeLoanApprovalStep> current = application.getStatus() == LoanApplicationStatus.PENDING_CUSTOM_APPROVAL
                ? LoanApplicationSupport.currentPending(steps) : Optional.empty();
        return steps.stream().anyMatch(s ->
                s.getApprovalStage() == LoanApprovalStage.CUSTOM
                        && Objects.equals(s.getApproverEmployeeId(), employeeId)
                        && (s.getDecision() != null
                        || current.map(c -> c.getId().equals(s.getId())).orElse(false)));
    }

    @Override
    public List<EmployeeLoanApprovalStep> findAssignedCustomTasks(Long employeeId) {
        List<EmployeeLoanApprovalStep> result = new ArrayList<>();
        for (EmployeeLoanApprovalStep step : stepRepository.findByApproverEmployeeIdAndDecisionIsNull(employeeId)) {
            if (step.getApprovalStage() != LoanApprovalStage.CUSTOM) continue;
            EmployeeLoanApplication app = applicationRepository.findById(step.getLoanApplicationId()).orElse(null);
            if (app == null || app.getStatus() != LoanApplicationStatus.PENDING_CUSTOM_APPROVAL) continue;
            boolean isCurrent = LoanApplicationSupport
                    .currentPending(stepRepository.findByLoanApplicationIdOrderBySequenceNoAscIdAsc(app.getId()))
                    .map(c -> c.getId().equals(step.getId())).orElse(false);
            if (isCurrent) result.add(step);
        }
        return result;
    }

    @Override
    public Map<Long, String> employeeNames(Collection<Long> employeeIds) {
        // HashMap on purpose: callers look up nullable ids (e.g. actedBy on a pending step, or the approver
        // of a group-routed HR/Finance step), and immutable maps such as Map.of() throw NPE on get(null).
        if (employeeIds == null || employeeIds.isEmpty()) return new HashMap<>();
        Set<Long> ids = employeeIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return new HashMap<>();
        return employeeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getFullName, (a, b) -> a, HashMap::new));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}