package com.justjava.humanresource.payroll.service.diagnostics;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class PayrollJournalDiagnostics {
    private final Long companyId;
    private final Long payrollPeriodId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final List<PayrollJournalDiagnosticLine> generatedLines = new ArrayList<>();
    private final List<PayrollJournalDiagnosticLine> skippedLines = new ArrayList<>();
    private final List<PayrollRunDiagnosticSummary> payrollRuns = new ArrayList<>();
    private BigDecimal totalDebit = BigDecimal.ZERO;
    private BigDecimal totalCredit = BigDecimal.ZERO;

    public PayrollJournalDiagnostics(Long companyId, Long payrollPeriodId, LocalDate periodStart, LocalDate periodEnd) {
        this.companyId = companyId;
        this.payrollPeriodId = payrollPeriodId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public void addGeneratedLine(PayrollJournalDiagnosticLine line) {
        generatedLines.add(line);
    }

    public void addSkippedLine(PayrollJournalDiagnosticLine line) {
        skippedLines.add(line);
    }

    public void addPayrollRun(PayrollRunDiagnosticSummary run) {
        payrollRuns.add(run);
    }

    public void setTotals(BigDecimal totalDebit, BigDecimal totalCredit) {
        this.totalDebit = safe(totalDebit);
        this.totalCredit = safe(totalCredit);
    }

    public BigDecimal getDifference() {
        return totalDebit.subtract(totalCredit);
    }

    public int getGeneratedLineCount() {
        return generatedLines.size();
    }

    public int getSkippedLineCount() {
        return skippedLines.size();
    }

    public List<PayrollJournalSummaryLine> getComponentSummaries() {
        Map<String, BigDecimal[]> grouped = new LinkedHashMap<>();
        for (PayrollJournalDiagnosticLine line : generatedLines) {
            String label = line.getComponentType() + " / " + line.getComponentCode() + " - " + line.getComponentName();
            BigDecimal[] totals = grouped.computeIfAbsent(label, key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(safe(line.getDebitAmount()));
            totals[1] = totals[1].add(safe(line.getCreditAmount()));
        }
        return toSummaryLines(grouped);
    }

    public List<PayrollJournalSummaryLine> getAccountSummaries() {
        Map<String, BigDecimal[]> grouped = new LinkedHashMap<>();
        for (PayrollJournalDiagnosticLine line : generatedLines) {
            String label = line.getAccountCode() + " - " + line.getAccountName();
            BigDecimal[] totals = grouped.computeIfAbsent(label, key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(safe(line.getDebitAmount()));
            totals[1] = totals[1].add(safe(line.getCreditAmount()));
        }
        return toSummaryLines(grouped);
    }

    public List<PayrollJournalSummaryLine> getSkipReasonSummaries() {
        Map<String, BigDecimal[]> grouped = new LinkedHashMap<>();
        for (PayrollJournalDiagnosticLine line : skippedLines) {
            BigDecimal[] totals = grouped.computeIfAbsent(line.getSkipReason(), key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(safe(line.getPayrollLineAmount()));
        }
        return toSummaryLines(grouped);
    }

    public List<PayrollJournalDiagnosticLine> getTopGeneratedLines() {
        return generatedLines.stream()
                .sorted(Comparator.comparing(this::lineMagnitude).reversed())
                .limit(50)
                .collect(Collectors.toList());
    }

    public String toLogReport() {
        StringBuilder report = new StringBuilder();
        report.append(System.lineSeparator()).append("PAYROLL JOURNAL IMBALANCE").append(System.lineSeparator());
        report.append("Company: ").append(companyId).append(System.lineSeparator());
        report.append("Period: ").append(payrollPeriodId).append(" ")
                .append(periodStart).append(" to ").append(periodEnd).append(System.lineSeparator());
        report.append("Total Debit: ").append(totalDebit).append(System.lineSeparator());
        report.append("Total Credit: ").append(totalCredit).append(System.lineSeparator());
        report.append("Difference: ").append(getDifference()).append(System.lineSeparator());
        report.append("Generated Lines: ").append(getGeneratedLineCount()).append(System.lineSeparator());
        report.append("Skipped Lines: ").append(getSkippedLineCount()).append(System.lineSeparator());

        appendSummary(report, "Totals by component", getComponentSummaries());
        appendSummary(report, "Totals by account", getAccountSummaries());
        appendSummary(report, "Skipped line reasons", getSkipReasonSummaries());

        report.append("Payroll runs included:").append(System.lineSeparator());
        for (PayrollRunDiagnosticSummary run : payrollRuns) {
            report.append("  Run #").append(run.getPayrollRunId())
                    .append(" | ").append(run.getEmployeeNumber()).append(" ").append(run.getEmployeeName())
                    .append(" | Gross ").append(run.getGrossPay())
                    .append(" | Deductions ").append(run.getTotalDeductions())
                    .append(" | Net ").append(run.getNetPay())
                    .append(" | Included lines ").append(run.getIncludedLineItemsTotal())
                    .append(" | Skipped lines ").append(run.getSkippedLineItemsTotal())
                    .append(System.lineSeparator());
        }

        report.append("Top generated lines:").append(System.lineSeparator());
        for (PayrollJournalDiagnosticLine line : getTopGeneratedLines()) {
            report.append("  Run #").append(line.getPayrollRunId())
                    .append(" | ").append(line.getEmployeeNumber()).append(" ").append(line.getEmployeeName())
                    .append(" | ").append(line.getComponentType()).append("/").append(line.getComponentCode())
                    .append(" | ").append(line.getLineType())
                    .append(" | ").append(line.getAccountCode()).append(" ").append(line.getAccountName())
                    .append(" | Debit ").append(safe(line.getDebitAmount()))
                    .append(" | Credit ").append(safe(line.getCreditAmount()))
                    .append(" | Account source ").append(line.getAccountSource())
                    .append(System.lineSeparator());
        }
        return report.toString();
    }

    private void appendSummary(StringBuilder report, String title, List<PayrollJournalSummaryLine> lines) {
        report.append(title).append(":").append(System.lineSeparator());
        if (lines.isEmpty()) {
            report.append("  None").append(System.lineSeparator());
            return;
        }
        for (PayrollJournalSummaryLine line : lines) {
            report.append("  ").append(line.getLabel())
                    .append(" | Debit ").append(line.getDebitAmount())
                    .append(" | Credit ").append(line.getCreditAmount())
                    .append(" | Difference ").append(line.getDifference())
                    .append(System.lineSeparator());
        }
    }

    private List<PayrollJournalSummaryLine> toSummaryLines(Map<String, BigDecimal[]> grouped) {
        return grouped.entrySet().stream()
                .map(entry -> new PayrollJournalSummaryLine(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparing(line -> line.getDebitAmount().add(line.getCreditAmount()), Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private BigDecimal lineMagnitude(PayrollJournalDiagnosticLine line) {
        return safe(line.getDebitAmount()).add(safe(line.getCreditAmount()));
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
