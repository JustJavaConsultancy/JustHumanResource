package com.justjava.humanresource.payroll.report.services;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayrollRunType;
import com.justjava.humanresource.payroll.report.dto.LineVarianceDTO;
import com.justjava.humanresource.payroll.report.dto.PayrollVarianceDTO;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollVarianceServiceImpl implements PayrollVarianceService {

    private final PayrollRunRepository              payrollRunRepository;
    private final PayrollLineItemRepository         payrollLineItemRepository;
    private final PayrollPeriodRepository           payrollPeriodRepository;
    private final EmployeePositionHistoryRepository positionHistoryRepository;

    // Component codes that are salary-driven (not standalone allowances).
    // Changes to these are reported as "Salary change", not "Allowance change".
    private static final Set<String> SALARY_CODES =
            Set.of("BASIC", "RESIDUAL");

    @Override
    @Transactional(readOnly = true)
    public List<PayrollVarianceDTO> generateVarianceReport(
            Long      companyId,
            LocalDate currentPeriodStart,
            LocalDate currentPeriodEnd,
            Long      employeeId,
            Long      departmentId) {

        // ── 1. Load all latest POSTED runs for the current period ──────────────
        List<PayrollRun> currentRuns = payrollRunRepository
                .findLatestByCompanyAndPeriodAndStatus(
                        companyId,
                        currentPeriodStart,
                        currentPeriodEnd,
                        PayrollRunStatus.POSTED
                );

        // ── 2. Optional filters ───────────────────────────────────────────────
        if (employeeId != null) {
            currentRuns = currentRuns.stream()
                    .filter(r -> r.getEmployee().getId().equals(employeeId))
                    .collect(Collectors.toList());
        }
        if (departmentId != null) {
            currentRuns = currentRuns.stream()
                    .filter(r -> r.getEmployee().getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }

        if (currentRuns.isEmpty()) return Collections.emptyList();

        // ── 3. Resolve previous period ────────────────────────────────────────
        PayrollPeriod previousPeriod = resolvePreviousPeriod(companyId, currentPeriodStart);

        // ── 4. Build one variance row per employee ────────────────────────────
        List<PayrollVarianceDTO> result = new ArrayList<>();
        for (PayrollRun current : currentRuns) {
            result.add(buildVariance(current, previousPeriod));
        }
        return result;
    }


    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Finds the period whose {@code periodEnd} is immediately before
     * {@code currentPeriodStart}. Returns null when no prior period exists
     * (i.e., the first ever payroll period for the company).
     */
    private PayrollPeriod resolvePreviousPeriod(Long companyId, LocalDate currentPeriodStart) {
        return payrollPeriodRepository
                .findByCompanyIdOrderByPeriodStartDesc(companyId)
                .stream()
                .filter(p -> p.getPeriodEnd().isBefore(currentPeriodStart))
                .findFirst()
                .orElse(null);
    }


    /**
     * Builds the full {@link PayrollVarianceDTO} for one employee by comparing
     * their current-period run against the previous-period run.
     */
    private PayrollVarianceDTO buildVariance(PayrollRun current, PayrollPeriod prevPeriod) {

        Employee emp = current.getEmployee();

        // Current line items
        List<PayrollLineItem> currentLines =
                payrollLineItemRepository.findByPayrollRunId(current.getId());

        // Previous run + line items
        PayrollRun             previousRun   = null;
        List<PayrollLineItem>  previousLines = Collections.emptyList();

        if (prevPeriod != null) {
            previousRun = payrollRunRepository.findLatestPostedRunForEmployeeAndPeriod(
                    emp.getId(),
                    prevPeriod.getPeriodStart(),
                    prevPeriod.getPeriodEnd()
            ).orElse(null);

            if (previousRun != null) {
                previousLines = payrollLineItemRepository.findByPayrollRunId(previousRun.getId());
            }
        }

        boolean isNew = (previousRun == null);

        BigDecimal prevGross = previousRun != null ? safe(previousRun.getGrossPay())       : BigDecimal.ZERO;
        BigDecimal prevDed   = previousRun != null ? safe(previousRun.getTotalDeductions()) : BigDecimal.ZERO;
        BigDecimal prevNet   = previousRun != null ? safe(previousRun.getNetPay())          : BigDecimal.ZERO;

        // Per-component diff
        List<LineVarianceDTO> lineVariances = buildLineVariances(currentLines, previousLines);

        // Reason detection
        List<String> reasons = detectReasons(current, previousRun, lineVariances, emp);

        return PayrollVarianceDTO.builder()
                .employeeId(emp.getId())
                .employeeNumber(emp.getEmployeeNumber())
                .employeeName(emp.getFullName())
                .department(emp.getDepartment().getName())
                .currentGross(safe(current.getGrossPay()))
                .currentDeductions(safe(current.getTotalDeductions()))
                .currentNet(safe(current.getNetPay()))
                .previousGross(prevGross)
                .previousDeductions(prevDed)
                .previousNet(prevNet)
                .grossVariance(safe(current.getGrossPay()).subtract(prevGross))
                .deductionsVariance(safe(current.getTotalDeductions()).subtract(prevDed))
                .netVariance(safe(current.getNetPay()).subtract(prevNet))
                .lineVariances(lineVariances)
                .reasons(reasons)
                .newEmployee(isNew)
                .amendment(current.getRunType() == PayrollRunType.AMENDMENT)
                .amendmentReason(current.getAmendmentReason())
                .build();
    }


    /**
     * Compares two sets of line items and classifies every component code as
     * ADDED, REMOVED, CHANGED, or UNCHANGED.
     */
    private List<LineVarianceDTO> buildLineVariances(
            List<PayrollLineItem> currentLines,
            List<PayrollLineItem> previousLines) {

        // Index by component code
        Map<String, PayrollLineItem> currentMap = currentLines.stream()
                .collect(Collectors.toMap(
                        PayrollLineItem::getComponentCode,
                        l -> l,
                        (a, b) -> a               // keep first on collision (shouldn't happen)
                ));
        Map<String, PayrollLineItem> previousMap = previousLines.stream()
                .collect(Collectors.toMap(
                        PayrollLineItem::getComponentCode,
                        l -> l,
                        (a, b) -> a
                ));

        // Union of all codes, deterministic order
        Set<String> allCodes = new LinkedHashSet<>();
        allCodes.addAll(currentMap.keySet());
        allCodes.addAll(previousMap.keySet());

        List<LineVarianceDTO> result = new ArrayList<>();

        for (String code : allCodes) {
            PayrollLineItem cur  = currentMap.get(code);
            PayrollLineItem prev = previousMap.get(code);

            BigDecimal curAmt  = cur  != null ? safe(cur.getAmount())  : null;
            BigDecimal prevAmt = prev != null ? safe(prev.getAmount()) : null;

            BigDecimal variance = safe(curAmt).subtract(safe(prevAmt));

            String changeType;
            if (cur == null) {
                changeType = "REMOVED";
            } else if (prev == null) {
                changeType = "ADDED";
            } else if (variance.compareTo(BigDecimal.ZERO) != 0) {
                changeType = "CHANGED";
            } else {
                changeType = "UNCHANGED";
            }

            String componentType = cur  != null ? cur.getComponentType().name()
                                 : prev != null ? prev.getComponentType().name()
                                 : "UNKNOWN";

            String description   = cur  != null ? cur.getDescription()
                                 : prev != null ? prev.getDescription()
                                 : code;

            result.add(new LineVarianceDTO(
                    code,
                    description,
                    componentType,
                    prevAmt,
                    curAmt,
                    variance,
                    changeType
            ));
        }

        return result;
    }


    /**
     * Derives human-readable reasons for the observed variance.
     * Multiple reasons may apply simultaneously.
     */
    private List<String> detectReasons(
            PayrollRun           current,
            PayrollRun           previousRun,
            List<LineVarianceDTO> lineVariances,
            Employee             emp) {

        List<String> reasons = new ArrayList<>();

        // ── New employee ──────────────────────────────────────────────────────
        if (previousRun == null) {
            reasons.add("New employee");
            return reasons;                   // no further comparison possible
        }

        // ── Amendment ────────────────────────────────────────────────────────
        if (current.getRunType() == PayrollRunType.AMENDMENT) {
            reasons.add("Amendment");
        }

        // ── Salary change / Promotion ─────────────────────────────────────────
        // Detected when a new EmployeePositionHistory record became effective
        // within the current period and points to a different JobStep than the
        // previous run's effective position.
        List<EmployeePositionHistory> positionChanges =
                positionHistoryRepository.findByEmployee_IdAndEffectiveFromBetween(
                        emp.getId(),
                        current.getPeriodStart(),
                        current.getPeriodEnd()
                );

        if (!positionChanges.isEmpty()) {
            // Check whether the job step actually changed (vs. a same-step re-record)
            Long prevJobStepId = previousRun.getEmployee().getJobStep() != null
                    ? previousRun.getEmployee().getJobStep().getId() : null;
            Long currJobStepId = emp.getJobStep() != null
                    ? emp.getJobStep().getId() : null;

            if (prevJobStepId != null && !prevJobStepId.equals(currJobStepId)) {
                reasons.add("Promotion / salary grade change");
            } else {
                reasons.add("Salary change");
            }
        }

        // ── Allowance added / removed / changed ───────────────────────────────
        boolean allowanceAdded   = false;
        boolean allowanceRemoved = false;
        boolean allowanceChanged = false;

        for (LineVarianceDTO line : lineVariances) {
            if ("EARNING".equals(line.getComponentType())
                    && !SALARY_CODES.contains(line.getComponentCode())) {
                switch (line.getChangeType()) {
                    case "ADDED"   -> allowanceAdded   = true;
                    case "REMOVED" -> allowanceRemoved = true;
                    case "CHANGED" -> allowanceChanged = true;
                }
            }
        }
        if (allowanceAdded)   reasons.add("Allowance added");
        if (allowanceRemoved) reasons.add("Allowance removed");
        if (allowanceChanged) reasons.add("Allowance change");

        // ── KPI adjustment ────────────────────────────────────────────────────
        // Gross changed, but no position-history change recorded in the period
        // → the only driver is a different KPI score.
        boolean grossChanged = safe(current.getGrossPay())
                .compareTo(safe(previousRun.getGrossPay())) != 0;
        boolean positionChanged = !positionChanges.isEmpty();

        if (grossChanged && !positionChanged) {
            reasons.add("KPI adjustment");
        }

        // ── Deduction change ──────────────────────────────────────────────────
        boolean deductionChanged = lineVariances.stream()
                .anyMatch(l -> "DEDUCTION".equals(l.getComponentType())
                        && !"UNCHANGED".equals(l.getChangeType()));
        if (deductionChanged) reasons.add("Deduction change");

        // ── No change ─────────────────────────────────────────────────────────
        if (reasons.isEmpty()) {
            reasons.add("No change");
        }

        return reasons;
    }


    /** Null-safe BigDecimal coercion to ZERO. */
    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
