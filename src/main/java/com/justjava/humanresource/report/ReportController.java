package com.justjava.humanresource.report;

import com.justjava.humanresource.payroll.report.dto.*;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/payroll")
public class ReportController {

    private static final Long COMPANY_ID = 1L;

    // Fixed date range: January 1 – December 31 of the current year
    private static final LocalDate START_DATE = LocalDate.of(LocalDate.now().getYear(), 1, 1);
    private static final LocalDate END_DATE   = LocalDate.of(LocalDate.now().getYear(), 12, 31);

    private final PayrollRunService payrollRunService;

    public ReportController(PayrollRunService payrollRunService) {
        this.payrollRunService = payrollRunService;
    }

    @GetMapping("/master-report")
    public String masterPayrollReport(Model model) {

        System.out.println("This is the component trend " + payrollRunService.getComponentTrend(COMPANY_ID));
        System.out.println("This is the payroll summary " + payrollRunService.getPayrollSummary(COMPANY_ID, START_DATE, END_DATE));
        System.out.println("This is the earnings breakdown " + payrollRunService.getEarningsBreakdown(COMPANY_ID, START_DATE, END_DATE));
        System.out.println("This is the deduction breakdown " + payrollRunService.getDeductionBreakdown(COMPANY_ID, START_DATE, END_DATE));
        System.out.println("This is the PAYE report " + payrollRunService.getPayeReport(COMPANY_ID, START_DATE, END_DATE));
        System.out.println("This is the pension report " + payrollRunService.getPensionReport(COMPANY_ID, START_DATE, END_DATE));

        // ── 1. Department / Group Summary Cards ──────────────────────────────────
        List<PayrollSummaryDTO> payrollSummary =
                payrollRunService.getPayrollSummary(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("payrollSummary", payrollSummary);

        // ── 2. Grand totals derived from the summary list ────────────────────────
        double grandTotalGross      = payrollSummary.stream()
                .mapToDouble(s -> s.getTotalGross().doubleValue()).sum();
        double grandTotalDeductions = payrollSummary.stream()
                .mapToDouble(s -> s.getTotalDeductions().doubleValue()).sum();
        double grandTotalNet        = payrollSummary.stream()
                .mapToDouble(s -> s.getTotalNet().doubleValue()).sum();

        model.addAttribute("grandTotalGross",      grandTotalGross);
        model.addAttribute("grandTotalDeductions", grandTotalDeductions);
        model.addAttribute("grandTotalNet",        grandTotalNet);

        // ── 3. Earnings Breakdown (Basic, Housing, Transport, etc.) ──────────────
        List<ComponentBreakdownDTO> earningsBreakdown =
                payrollRunService.getEarningsBreakdown(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("earningsBreakdown", earningsBreakdown);

        // Derive the max earnings amount for proportional bar widths in the UI
        double maxEarnings = earningsBreakdown.stream()
                .mapToDouble(c -> c.getTotalAmount().doubleValue())
                .max()
                .orElse(1.0);
        model.addAttribute("maxEarnings", maxEarnings);

        // ── 4. Deduction Breakdown (PAYE, Pension, Loan, etc.) ───────────────────
        List<ComponentBreakdownDTO> deductionBreakdown =
                payrollRunService.getDeductionBreakdown(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("deductionBreakdown", deductionBreakdown);

        // ── 5. Component Trend (monthly series for bar chart) ────────────────────
        List<ComponentTrendDTO> componentTrend =
                payrollRunService.getComponentTrend(COMPANY_ID);
        model.addAttribute("componentTrend", componentTrend);

        // Collect distinct periods for the chart's X-axis labels
        List<String> trendPeriods = componentTrend.stream()
                .map(ComponentTrendDTO::getPeriod)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("trendPeriods", trendPeriods);

        // Collect distinct component codes present in the trend data
        List<String> trendComponents = componentTrend.stream()
                .map(ComponentTrendDTO::getComponentCode)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("trendComponents", trendComponents);

        // Max trend amount – used for proportional bar heights
        double maxTrendAmount = componentTrend.stream()
                .mapToDouble(c -> c.getTotalAmount().doubleValue())
                .max()
                .orElse(1.0);
        model.addAttribute("maxTrendAmount", maxTrendAmount);

        // ── 6. PAYE Tax Report ────────────────────────────────────────────────────
        List<PayeReportDTO> payeReport =
                payrollRunService.getPayeReport(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("payeReport", payeReport);

        // ── 7. Pension Contribution Report ───────────────────────────────────────
        List<PensionReportDTO> pensionReport =
                payrollRunService.getPensionReport(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("pensionReport", pensionReport);

        // ── 8. Report metadata ───────────────────────────────────────────────────
        model.addAttribute("reportYear",  LocalDate.now().getYear());
        model.addAttribute("startDate",   START_DATE);
        model.addAttribute("endDate",     END_DATE);

        return "payroll/master-report";   // resolves to templates/payroll/master-report.html
    }
}