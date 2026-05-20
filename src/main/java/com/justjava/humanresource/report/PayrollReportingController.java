package com.justjava.humanresource.report;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.payroll.report.dto.*;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.justjava.humanresource.hr.service.JobHrEmployeeAccessService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import java.util.Set;


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
    private final AuthenticationManager authenticationManager;
    private final JobHrEmployeeAccessService jobHrEmployeeAccessService;
    private final EmployeeOnboardingService employeeOnboardingService;

    public PayrollReportingController(PayrollRunService payrollRunService,
                                      AuthenticationManager authenticationManager,
                                      JobHrEmployeeAccessService jobHrEmployeeAccessService,
                                      EmployeeOnboardingService employeeOnboardingService) {
        this.payrollRunService = payrollRunService;
        this.authenticationManager = authenticationManager;
        this.jobHrEmployeeAccessService = jobHrEmployeeAccessService;
        this.employeeOnboardingService = employeeOnboardingService;
    }

    @GetMapping("/reporting")
    public String payrollReporting(Model model, Map map) {
        if (authenticationManager.isRestrictedHr()) return "redirect:/employees";

        // ── Resolve scope ────────────────────────────────────────────────────────
        boolean isJobHr = jobHrEmployeeAccessService.isJobHrScopedUser();
        List<Long> scopedIds = isJobHr
                ? employeeOnboardingService.getAllOnboardings()
                .stream().map(e -> e.getId()).collect(Collectors.toList())
                : null;

        // ── 1. Payroll Summary ───────────────────────────────────────────────────
        List<PayrollSummaryDTO> payrollSummary = isJobHr
                ? payrollRunService.getPayrollSummaryForEmployees(COMPANY_ID, START_DATE, END_DATE, scopedIds)
                : payrollRunService.getPayrollSummary(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("payrollSummary", payrollSummary);

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

        BigDecimal maxGroupGross = payrollSummary.stream()
                .map(s -> s.getTotalGross() != null ? s.getTotalGross() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        model.addAttribute("maxGroupGross", maxGroupGross);

        // ── 2. Earnings Breakdown ────────────────────────────────────────────────
        List<ComponentBreakdownDTO> earningsBreakdown = isJobHr
                ? payrollRunService.getEarningsBreakdownForEmployees(COMPANY_ID, START_DATE, END_DATE, scopedIds)
                : payrollRunService.getEarningsBreakdown(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("earningsBreakdown", earningsBreakdown);

        BigDecimal maxEarning = earningsBreakdown.stream()
                .map(e -> e.getTotalAmount() != null ? e.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        model.addAttribute("maxEarning", maxEarning);

        // ── 3. Deduction Breakdown ───────────────────────────────────────────────
        List<ComponentBreakdownDTO> deductionBreakdown = isJobHr
                ? payrollRunService.getDeductionBreakdownForEmployees(COMPANY_ID, START_DATE, END_DATE, scopedIds)
                : payrollRunService.getDeductionBreakdown(COMPANY_ID, START_DATE, END_DATE);
        model.addAttribute("deductionBreakdown", deductionBreakdown);

        BigDecimal maxDeduction = deductionBreakdown.stream()
                .map(d -> d.getTotalAmount() != null ? d.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        model.addAttribute("maxDeduction", maxDeduction);

        // ── 4. Component Trend ───────────────────────────────────────────────────
        List<ComponentTrendDTO> componentTrend = isJobHr
                ? payrollRunService.getComponentTrendForEmployees(COMPANY_ID, scopedIds)
                : payrollRunService.getComponentTrend(COMPANY_ID);
        model.addAttribute("componentTrend", componentTrend);

        List<String> trendPeriods = componentTrend.stream()
                .map(ComponentTrendDTO::getPeriod).filter(p -> p != null)
                .distinct().sorted().collect(Collectors.toList());
        model.addAttribute("trendPeriods", trendPeriods);

        List<String> trendCodes = componentTrend.stream()
                .map(ComponentTrendDTO::getComponentCode).filter(c -> c != null)
                .distinct().collect(Collectors.toList());
        model.addAttribute("trendCodes", trendCodes);

        BigDecimal maxTrendAmount = componentTrend.stream()
                .map(t -> t.getTotalAmount() != null ? t.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        model.addAttribute("maxTrendAmount", maxTrendAmount);

        // ── 5. PAYE preview (already filterable by employeeId) ───────────────────
        List<PayeReportDTO> payeReport =
                payrollRunService.getPayeReport(COMPANY_ID, START_DATE, END_DATE);
        if (isJobHr) {
            Set<Long> scopedSet = new java.util.HashSet<>(scopedIds);
            payeReport = payeReport.stream()
                    .filter(p -> scopedSet.contains(p.getEmployeeId()))
                    .collect(Collectors.toList());
        }
        List<PayeReportDTO> recentEntries = payeReport.stream().limit(5).collect(Collectors.toList());
        model.addAttribute("recentEntries",  recentEntries);
        model.addAttribute("totalEmployees", payeReport.size());

        // ── 6. Pension count ─────────────────────────────────────────────────────
        List<PensionReportDTO> pensionReport =
                payrollRunService.getPensionReport(COMPANY_ID, START_DATE, END_DATE);
        if (isJobHr) {
            Set<Long> scopedSet = new java.util.HashSet<>(scopedIds);
            pensionReport = pensionReport.stream()
                    .filter(p -> scopedSet.contains(p.getEmployeeId()))
                    .collect(Collectors.toList());
        }
        model.addAttribute("pensionEmployeeCount", pensionReport.size());

        // ── Metadata ─────────────────────────────────────────────────────────────
        model.addAttribute("reportYear", LocalDate.now().getYear());
        model.addAttribute("startDate",  START_DATE);
        model.addAttribute("endDate",    END_DATE);
        model.addAttribute("title", "Reporting");
        model.addAttribute("subTitle", "A detailed overview of financial activities for the current year.");

        return "payroll/reporting";
    }
}