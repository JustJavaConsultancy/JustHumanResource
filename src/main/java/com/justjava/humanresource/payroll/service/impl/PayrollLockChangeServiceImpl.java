package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.dto.PayrollLockChangeReportDTO;
import com.justjava.humanresource.payroll.dto.PayrollLockChangeType;
import com.justjava.humanresource.payroll.dto.PayrollLockEmployeeChangeDTO;
import com.justjava.humanresource.payroll.dto.PayrollLockLineChangeDTO;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PayrollLockChangeService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * READ-ONLY. Never writes, never touches payroll, journal or workflow state.
 *
 * Before = per employee, the highest-version POSTED run created at or before
 *          the Finance task's creation time (mirrors how the journal chose runs).
 * Now    = per employee, the highest-version run for the period today (any status).
 *
 * When both runs are POSTED the individual pay items are compared as well, using
 * the same inclusion rule as the journal (out-of-payroll, null, zero and negative
 * lines are ignored), so a swap between pay items with identical totals is caught.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollLockChangeServiceImpl implements PayrollLockChangeService {

    private static final DateTimeFormatter LOCKED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollLineItemRepository payrollLineItemRepository;

    /* ============================================================
       PUBLIC API
       ============================================================ */

    @Override
    public Map<String, PayrollLockChangeReportDTO> buildReports(List<FlowableTaskDTO> tasks) {
        Map<String, PayrollLockChangeReportDTO> reports = new LinkedHashMap<>();
        if (tasks == null) {
            return reports;
        }
        for (FlowableTaskDTO task : tasks) {
            reports.put(task.getTaskId(), buildReport(task));
        }
        return reports;
    }

    @Override
    public PayrollLockChangeReportDTO buildReport(FlowableTaskDTO task) {

        if (task == null) {
            log.warn("Payroll lock change report requested for a null task");
            return unavailable(null, null, null);
        }

        Long periodId = readPeriodId(task);
        if (periodId == null) {
            log.warn("Payroll lock change report: no readable periodId for task {}", task.getTaskId());
            return unavailable(task, null, task.getCreatedTime());
        }

        PayrollPeriod period = payrollPeriodRepository.findById(periodId).orElse(null);
        if (period == null) {
            log.warn("Payroll lock change report: period {} not found for task {}", periodId, task.getTaskId());
            return unavailable(task, periodId, task.getCreatedTime());
        }

        LocalDateTime cutoff = task.getCreatedTime();
        if (cutoff == null) {
            log.warn("Payroll lock change report: task {} has no created time", task.getTaskId());
            return unavailable(task, periodId, null);
        }

        Long companyId = period.getCompanyId();
        LocalDate start = period.getPeriodStart();
        LocalDate end = period.getPeriodEnd();

        Map<Long, PayrollRun> before = indexByEmployee(
                payrollRunRepository.findLatestPostedRunsAsOf(companyId, start, end, cutoff));
        Map<Long, PayrollRun> now = indexByEmployee(
                payrollRunRepository.findLatestRunsPerEmployeeForPeriod(companyId, start, end));

        Set<Long> employeeIds = new LinkedHashSet<>(before.keySet());
        employeeIds.addAll(now.keySet());

        Map<Long, List<PayrollLineItem>> linesByRun = loadLinesForComparison(before, now);

        List<PayrollLockEmployeeChangeDTO> rows = new ArrayList<>();
        int changed = 0, added = 0, removed = 0, recalculating = 0, lineItemsChanged = 0;
        int pendingExcludedFromNow = 0;

        for (Long employeeId : employeeIds) {
            PayrollRun b = before.get(employeeId);
            PayrollRun n = now.get(employeeId);

            if (b != null && n != null) {
                if (n.getStatus() != PayrollRunStatus.POSTED) {
                    rows.add(recalculatingRow(b, n));
                    recalculating++;
                    pendingExcludedFromNow++;
                } else {
                    List<PayrollLockLineChangeDTO> lineChanges = compareLines(b, n, linesByRun);
                    PayrollLockEmployeeChangeDTO row = changedRow(b, n);
                    if (row != null) {
                        row.setLineChanges(lineChanges);
                        rows.add(row);
                        changed++;
                    } else if (!lineChanges.isEmpty()) {
                        // Same gross, deductions and net, but the pay items differ.
                        rows.add(lineItemsRow(b, n, lineChanges));
                        lineItemsChanged++;
                    }
                }
            } else if (n != null) {
                rows.add(addedRow(n));
                added++;
                if (n.getStatus() != PayrollRunStatus.POSTED) {
                    pendingExcludedFromNow++;
                }
            } else {
                rows.add(removedRow(b));
                removed++;
            }
        }

        rows.sort(Comparator.comparing(
                PayrollLockEmployeeChangeDTO::getEmployeeName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        /* ---------- totals ---------- */
        BigDecimal beforeGross = BigDecimal.ZERO, beforeDed = BigDecimal.ZERO, beforeNet = BigDecimal.ZERO;
        for (PayrollRun r : before.values()) {
            beforeGross = beforeGross.add(nz(r.getGrossPay()));
            beforeDed = beforeDed.add(nz(r.getTotalDeductions()));
            beforeNet = beforeNet.add(nz(r.getNetPay()));
        }

        BigDecimal afterGross = BigDecimal.ZERO, afterDed = BigDecimal.ZERO, afterNet = BigDecimal.ZERO;
        for (PayrollRun r : now.values()) {
            if (r.getStatus() == PayrollRunStatus.POSTED) {
                afterGross = afterGross.add(nz(r.getGrossPay()));
                afterDed = afterDed.add(nz(r.getTotalDeductions()));
                afterNet = afterNet.add(nz(r.getNetPay()));
            }
        }

        boolean hasChanges = !rows.isEmpty();

        String summary = null;
        if (hasChanges) {
            summary = "HR changed payroll after this period was locked on "
                    + cutoff.format(LOCKED_AT_FORMAT) + ". "
                    + rows.size() + " employee(s) are affected. "
                    + "Approval is blocked until you reject this request and HR locks the period again.";
            if (pendingExcludedFromNow > 0) {
                summary += " Employees still being recalculated are excluded from the \"Now\" totals.";
            }
        }

        return PayrollLockChangeReportDTO.builder()
                .taskId(task.getTaskId())
                .periodId(periodId)
                .lockedAt(cutoff)
                .hasChanges(hasChanges)
                .unavailable(false)
                .changedEmployeeCount(changed)
                .addedEmployeeCount(added)
                .removedEmployeeCount(removed)
                .recalculatingEmployeeCount(recalculating)
                .lineItemsChangedEmployeeCount(lineItemsChanged)
                .beforeTotalGross(beforeGross)
                .beforeTotalDeductions(beforeDed)
                .beforeTotalNet(beforeNet)
                .afterTotalGross(afterGross)
                .afterTotalDeductions(afterDed)
                .afterTotalNet(afterNet)
                .grossTotalDiff(afterGross.subtract(beforeGross))
                .deductionsTotalDiff(afterDed.subtract(beforeDed))
                .netTotalDiff(afterNet.subtract(beforeNet))
                .summaryMessage(summary)
                .employees(rows)
                .build();
    }

    /* ============================================================
       ROW BUILDERS
       ============================================================ */

    /** Returns null when gross, deductions and net are all unchanged. */
    private PayrollLockEmployeeChangeDTO changedRow(PayrollRun b, PayrollRun n) {

        boolean grossChanged = differs(b.getGrossPay(), n.getGrossPay());
        boolean deductionsChanged = differs(b.getTotalDeductions(), n.getTotalDeductions());
        boolean netChanged = differs(b.getNetPay(), n.getNetPay());

        if (!grossChanged && !deductionsChanged && !netChanged) {
            return null;
        }

        BigDecimal bg = nz(b.getGrossPay()), bd = nz(b.getTotalDeductions()), bn = nz(b.getNetPay());
        BigDecimal ag = nz(n.getGrossPay()), ad = nz(n.getTotalDeductions()), an = nz(n.getNetPay());

        return base(n)
                .changeType(PayrollLockChangeType.CHANGED)
                .beforeVersion(b.getVersionNumber())
                .afterVersion(n.getVersionNumber())
                .beforeGross(bg).beforeDeductions(bd).beforeNet(bn)
                .afterGross(ag).afterDeductions(ad).afterNet(an)
                .grossDiff(ag.subtract(bg))
                .deductionsDiff(ad.subtract(bd))
                .netDiff(an.subtract(bn))
                .grossChanged(grossChanged)
                .deductionsChanged(deductionsChanged)
                .netChanged(netChanged)
                .description(describe(grossChanged, deductionsChanged, netChanged))
                .build();
    }

    private PayrollLockEmployeeChangeDTO recalculatingRow(PayrollRun b, PayrollRun n) {
        return base(n)
                .changeType(PayrollLockChangeType.RECALCULATING)
                .beforeVersion(b.getVersionNumber())
                .afterVersion(n.getVersionNumber())
                .beforeGross(nz(b.getGrossPay()))
                .beforeDeductions(nz(b.getTotalDeductions()))
                .beforeNet(nz(b.getNetPay()))
                .description("A new payroll amendment is still being calculated. Figures are not final.")
                .build();
    }

    private PayrollLockEmployeeChangeDTO addedRow(PayrollRun n) {
        PayrollLockEmployeeChangeDTO.PayrollLockEmployeeChangeDTOBuilder row = base(n)
                .changeType(PayrollLockChangeType.ADDED)
                .afterVersion(n.getVersionNumber())
                .description("New in payroll since the lock.");

        if (n.getStatus() == PayrollRunStatus.POSTED) {
            row.afterGross(nz(n.getGrossPay()))
                    .afterDeductions(nz(n.getTotalDeductions()))
                    .afterNet(nz(n.getNetPay()));
        }
        return row.build();
    }

    private PayrollLockEmployeeChangeDTO removedRow(PayrollRun b) {
        return base(b)
                .changeType(PayrollLockChangeType.REMOVED)
                .beforeVersion(b.getVersionNumber())
                .beforeGross(nz(b.getGrossPay()))
                .beforeDeductions(nz(b.getTotalDeductions()))
                .beforeNet(nz(b.getNetPay()))
                .description("Was in payroll at the lock but is no longer.")
                .build();
    }

    private PayrollLockEmployeeChangeDTO.PayrollLockEmployeeChangeDTOBuilder base(PayrollRun run) {
        return PayrollLockEmployeeChangeDTO.builder()
                .employeeId(run.getEmployee().getId())
                .employeeNumber(run.getEmployee().getEmployeeNumber())
                .employeeName(run.getEmployee().getFullName());
    }

    /** Same gross, deductions and net; only the pay items differ. */
    private PayrollLockEmployeeChangeDTO lineItemsRow(
            PayrollRun b, PayrollRun n, List<PayrollLockLineChangeDTO> lineChanges) {

        BigDecimal g = nz(n.getGrossPay()), d = nz(n.getTotalDeductions()), net = nz(n.getNetPay());
        return base(n)
                .changeType(PayrollLockChangeType.LINE_ITEMS_CHANGED)
                .beforeVersion(b.getVersionNumber())
                .afterVersion(n.getVersionNumber())
                .beforeGross(nz(b.getGrossPay()))
                .beforeDeductions(nz(b.getTotalDeductions()))
                .beforeNet(nz(b.getNetPay()))
                .afterGross(g).afterDeductions(d).afterNet(net)
                .grossDiff(g.subtract(nz(b.getGrossPay())))
                .deductionsDiff(d.subtract(nz(b.getTotalDeductions())))
                .netDiff(net.subtract(nz(b.getNetPay())))
                .description("Pay items changed even though gross pay, deductions and net pay are unchanged ("
                        + lineChanges.size() + " item(s) differ).")
                .lineChanges(lineChanges)
                .build();
    }

    /* ============================================================
       LINE ITEM COMPARISON
       ============================================================ */

    /** One query for every run that needs a pay-item comparison. */
    private Map<Long, List<PayrollLineItem>> loadLinesForComparison(
            Map<Long, PayrollRun> before, Map<Long, PayrollRun> now) {

        Set<Long> runIds = new LinkedHashSet<>();
        for (Map.Entry<Long, PayrollRun> e : before.entrySet()) {
            PayrollRun b = e.getValue();
            PayrollRun n = now.get(e.getKey());
            if (needsLineComparison(b, n)) {
                runIds.add(b.getId());
                runIds.add(n.getId());
            }
        }

        Map<Long, List<PayrollLineItem>> byRun = new HashMap<>();
        if (runIds.isEmpty()) {
            return byRun;
        }
        for (PayrollLineItem line : payrollLineItemRepository.findByPayrollRunIdIn(runIds)) {
            byRun.computeIfAbsent(line.getPayrollRun().getId(), k -> new ArrayList<>()).add(line);
        }
        return byRun;
    }

    /** Both runs exist, the latest is POSTED, and it really is a different run. */
    private boolean needsLineComparison(PayrollRun b, PayrollRun n) {
        return b != null && n != null
                && n.getStatus() == PayrollRunStatus.POSTED
                && b.getId() != null && n.getId() != null
                && !b.getId().equals(n.getId());
    }

    private List<PayrollLockLineChangeDTO> compareLines(
            PayrollRun b, PayrollRun n, Map<Long, List<PayrollLineItem>> linesByRun) {

        if (!needsLineComparison(b, n)) {
            return new ArrayList<>();
        }

        Map<String, LineTotal> beforeLines = aggregate(linesByRun.get(b.getId()));
        Map<String, LineTotal> nowLines = aggregate(linesByRun.get(n.getId()));

        Set<String> keys = new LinkedHashSet<>(beforeLines.keySet());
        keys.addAll(nowLines.keySet());

        List<PayrollLockLineChangeDTO> result = new ArrayList<>();
        for (String key : keys) {
            LineTotal bl = beforeLines.get(key);
            LineTotal nl = nowLines.get(key);

            String kind;
            if (bl != null && nl != null) {
                if (!differs(bl.amount, nl.amount)) {
                    continue;
                }
                kind = "CHANGED";
            } else if (nl != null) {
                kind = "ADDED";
            } else {
                kind = "REMOVED";
            }

            LineTotal ref = nl != null ? nl : bl;
            BigDecimal beforeAmt = bl != null ? bl.amount : null;
            BigDecimal afterAmt = nl != null ? nl.amount : null;

            result.add(PayrollLockLineChangeDTO.builder()
                    .componentType(ref.componentType)
                    .componentCode(ref.componentCode)
                    .description(ref.description)
                    .changeKind(kind)
                    .beforeAmount(beforeAmt)
                    .afterAmount(afterAmt)
                    .diff(nz(afterAmt).subtract(nz(beforeAmt)))
                    .build());
        }

        result.sort(Comparator.comparing(
                PayrollLockLineChangeDTO::getDescription,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return result;
    }

    /**
     * Sums amounts per (componentType, componentCode), keeping only lines the
     * journal would include: not out-of-payroll and with a positive amount.
     */
    private Map<String, LineTotal> aggregate(List<PayrollLineItem> lines) {
        Map<String, LineTotal> map = new LinkedHashMap<>();
        if (lines == null) {
            return map;
        }
        for (PayrollLineItem line : lines) {
            if (line.isOutOfPayroll()
                    || line.getAmount() == null
                    || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String type = line.getComponentType() != null ? line.getComponentType().name() : "";
            String code = line.getComponentCode() != null ? line.getComponentCode() : "";
            LineTotal total = map.computeIfAbsent(type + "|" + code,
                    k -> new LineTotal(type, code, line.getDescription()));
            total.amount = total.amount.add(line.getAmount());
        }
        return map;
    }

    private static final class LineTotal {
        final String componentType;
        final String componentCode;
        final String description;
        BigDecimal amount = BigDecimal.ZERO;

        LineTotal(String componentType, String componentCode, String description) {
            this.componentType = componentType;
            this.componentCode = componentCode;
            this.description = description;
        }
    }

    /* ============================================================
       WORDING
       ============================================================ */

    private String describe(boolean gross, boolean deductions, boolean net) {

        List<String> parts = new ArrayList<>();
        if (gross) parts.add(parts.isEmpty() ? "Gross pay" : "gross pay");
        if (deductions) parts.add(parts.isEmpty() ? "Deductions" : "deductions");
        if (net) parts.add(parts.isEmpty() ? "Net pay" : "net pay");

        String list;
        if (parts.size() == 1) {
            list = parts.get(0);
        } else if (parts.size() == 2) {
            list = parts.get(0) + " and " + parts.get(1);
        } else {
            list = parts.get(0) + ", " + parts.get(1) + " and " + parts.get(2);
        }

        // Situation B: net identical but gross AND deductions moved.
        if (gross && deductions && !net) {
            return list + " changed even though net pay is unchanged.";
        }
        return list + " changed.";
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private PayrollLockChangeReportDTO unavailable(FlowableTaskDTO task, Long periodId, LocalDateTime lockedAt) {
        return PayrollLockChangeReportDTO.builder()
                .taskId(task != null ? task.getTaskId() : null)
                .periodId(periodId)
                .lockedAt(lockedAt)
                .hasChanges(false)
                .unavailable(true)
                .beforeTotalGross(BigDecimal.ZERO)
                .beforeTotalDeductions(BigDecimal.ZERO)
                .beforeTotalNet(BigDecimal.ZERO)
                .afterTotalGross(BigDecimal.ZERO)
                .afterTotalDeductions(BigDecimal.ZERO)
                .afterTotalNet(BigDecimal.ZERO)
                .grossTotalDiff(BigDecimal.ZERO)
                .deductionsTotalDiff(BigDecimal.ZERO)
                .netTotalDiff(BigDecimal.ZERO)
                .employees(new ArrayList<>())
                .build();
    }

    /** periodId is stored as a Long process variable; read it as Number to be safe. */
    private Long readPeriodId(FlowableTaskDTO task) {
        Map<String, Object> vars = task.getVariables();
        if (vars == null) {
            return null;
        }
        Object raw = vars.get("periodId");
        return (raw instanceof Number) ? ((Number) raw).longValue() : null;
    }

    /** One run per employee; if ever duplicated, keep the highest version. */
    private Map<Long, PayrollRun> indexByEmployee(List<PayrollRun> runs) {
        Map<Long, PayrollRun> map = new HashMap<>();
        if (runs == null) {
            return map;
        }
        for (PayrollRun run : runs) {
            Long employeeId = run.getEmployee().getId();
            PayrollRun existing = map.get(employeeId);
            if (existing == null || version(run) > version(existing)) {
                map.put(employeeId, run);
            }
        }
        return map;
    }

    private int version(PayrollRun run) {
        return run.getVersionNumber() == null ? 0 : run.getVersionNumber();
    }

    /** Null counts as zero; compareTo so 400000.0 equals 400000.00. */
    private boolean differs(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) != 0;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}