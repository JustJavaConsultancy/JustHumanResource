package com.justjava.humanresource.payroll.controller;

import com.justjava.humanresource.payroll.report.dto.*;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PayrollReportingController {

    private static final Long COMPANY_ID = 1L;

    private static final LocalDate START_DATE = LocalDate.of(LocalDate.now().getYear(), 1, 1);
    private static final LocalDate END_DATE   = LocalDate.of(LocalDate.now().getYear(), 12, 31);

    private final PayrollRunService payrollRunService;

    public PayrollReportingController(PayrollRunService payrollRunService) {
        this.payrollRunService = payrollRunService;
    }

    @GetMapping("/reporting")
    public String payrollReporting(Model model,
                                   Map map) {

        // ── 1. Payroll Summary → summary cards + group focus bars ────────────────
        List<PayrollSummaryDTO> payrollSummary =
                payrollRunService.getPayrollSummary(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("payrollSummary", payrollSummary);

        // Grand totals for the four top-level stat cards (null-safe)
        BigDecimal grandTotalGross = payrollSummary.stream()
                .map(s -> s.getTotalGross() != null ? s.getTotalGross() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grandTotalNet = payrollSummary.stream()
                .map(s -> s.getTotalNet() != null ? s.getTotalNet() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grandTotalPaye = payrollSummary.stream()
                .map(s -> s.getTotalPaye() != null ? s.getTotalPaye() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grandTotalPension = payrollSummary.stream()
                .map(s -> s.getTotalPension() != null ? s.getTotalPension() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("grandTotalGross",   grandTotalGross);
        model.addAttribute("grandTotalNet",     grandTotalNet);
        model.addAttribute("grandTotalPaye",    grandTotalPaye);
        model.addAttribute("grandTotalPension", grandTotalPension);

        // Max gross across groups — used for proportional bar widths in Group Focus card
        BigDecimal maxGroupGross = payrollSummary.stream()
                .map(s -> s.getTotalGross() != null ? s.getTotalGross() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        model.addAttribute("maxGroupGross", maxGroupGross);

        // ── 2. Earnings Breakdown → bar chart section ────────────────────────────
        List<ComponentBreakdownDTO> earningsBreakdown =
                payrollRunService.getEarningsBreakdown(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("earningsBreakdown", earningsBreakdown);

        // Max earning amount for proportional bar widths (null-safe)
        BigDecimal maxEarning = earningsBreakdown.stream()
                .map(e -> e.getTotalAmount() != null ? e.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        model.addAttribute("maxEarning", maxEarning);

        // ── 3. Deduction Breakdown → bar chart section ───────────────────────────
        List<ComponentBreakdownDTO> deductionBreakdown =
                payrollRunService.getDeductionBreakdown(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("deductionBreakdown", deductionBreakdown);

        // Max deduction amount for proportional bar widths (null-safe)
        BigDecimal maxDeduction = deductionBreakdown.stream()
                .map(d -> d.getTotalAmount() != null ? d.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        model.addAttribute("maxDeduction", maxDeduction);

        // ── 4. Component Trend → line/dot chart ──────────────────────────────────
        List<ComponentTrendDTO> componentTrend =
                payrollRunService.getComponentTrend(COMPANY_ID);
        model.addAttribute("componentTrend", componentTrend);

        // Distinct sorted periods for X-axis labels
        List<String> trendPeriods = componentTrend.stream()
                .map(ComponentTrendDTO::getPeriod)
                .filter(p -> p != null)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        model.addAttribute("trendPeriods", trendPeriods);

        // Distinct component codes present in trend data
        List<String> trendCodes = componentTrend.stream()
                .map(ComponentTrendDTO::getComponentCode)
                .filter(c -> c != null)
                .distinct()
                .collect(Collectors.toList());
        model.addAttribute("trendCodes", trendCodes);

        // Max trend amount for proportional bar heights (null-safe)
        BigDecimal maxTrendAmount = componentTrend.stream()
                .map(t -> t.getTotalAmount() != null ? t.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        model.addAttribute("maxTrendAmount", maxTrendAmount);

        // ── 5. PAYE Report → "Recent Payroll Entries" table (first 5 rows) ───────
        List<PayeReportDTO> payeReport =
                payrollRunService.getPayeReport(COMPANY_ID, START_DATE, END_DATE);

        // Limit to 5 preview rows for the dashboard table
        List<PayeReportDTO> recentEntries = payeReport.stream()
                .limit(5)
                .collect(Collectors.toList());
        model.addAttribute("recentEntries",  recentEntries);
        model.addAttribute("totalEmployees", payeReport.size());

        // ── 6. Pension Report → feeds grandTotalPension already covered above ────
        //    Pass full list in case the template needs it for a badge/count
        List<PensionReportDTO> pensionReport =
                payrollRunService.getPensionReport(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("pensionEmployeeCount", pensionReport.size());

        // ── Metadata ─────────────────────────────────────────────────────────────
        model.addAttribute("reportYear", LocalDate.now().getYear());
        model.addAttribute("startDate",  START_DATE);
        model.addAttribute("endDate",    END_DATE);
        model.addAttribute("title", "Reporting");
        model.addAttribute("subTitle", "A detailed overview of financial activities for the current year.");

        return "payroll/reporting";   // → templates/payroll/reporting.html
    }
}