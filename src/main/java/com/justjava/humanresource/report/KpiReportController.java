package com.justjava.humanresource.report;

import com.justjava.humanresource.kpi.report.dto.*;
import com.justjava.humanresource.kpi.report.service.KPIReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Controller
@RequestMapping("/kpi-report")
public class KpiReportController {
    @Autowired
    private KPIReportService kpiReportService;

    private static final Long   COMPANY_ID = 1L;
    private static final int    TOP_N      = 10;


    // ─────────────────────────────────────────────────────────────────────────
    // DASHBOARD – hub using getKpiDashboard + getDepartmentKpiReport
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        YearMonth now = YearMonth.now();

        // Main dashboard data
        KpiDashboardDTO dashboard = kpiReportService.getKpiDashboard(now, 5, 5);
        model.addAttribute("dashboard", dashboard);

        // Distribution counts (null-safe)
        KpiDistributionDTO dist = (dashboard != null && dashboard.getDistribution() != null)
                ? dashboard.getDistribution() : null;

        int excellent = safeInt(Math.toIntExact(dist != null ? dist.getExcellent() : null));
        int good      = safeInt(Math.toIntExact(dist != null ? dist.getGood() : null));
        int average   = safeInt(Math.toIntExact(dist != null ? dist.getAverage() : null));
        int poor      = safeInt(Math.toIntExact(dist != null ? dist.getPoor() : null));
        int distTotal = excellent + good + average + poor;
        if (distTotal == 0) distTotal = 1; // prevent div-by-zero in template

        model.addAttribute("distExcellent", excellent);
        model.addAttribute("distGood",      good);
        model.addAttribute("distAverage",   average);
        model.addAttribute("distPoor",      poor);
        model.addAttribute("distTotal",     distTotal);

        // Department KPI overview for dashboard cards
        List<DepartmentKpiReportDTO> departments =
                kpiReportService.getDepartmentKpiReport(now);
        model.addAttribute("departments", departments);

        // Grand totals across departments (null-safe)
        int totalEmployees = departments.stream()
                .mapToInt(d -> Math.toIntExact(d.getTotalEmployees() != null ? d.getTotalEmployees() : 0))
                .sum();
        BigDecimal avgKpiScore = departments.isEmpty() ? BigDecimal.ZERO :
                departments.stream()
                        .map(d -> d.getAverageKpiScore() != null ? d.getAverageKpiScore() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(departments.size()), 2, java.math.RoundingMode.HALF_UP);

        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("avgKpiScore",    avgKpiScore);
        model.addAttribute("departmentCount", departments.size());

        // Period metadata
        model.addAttribute("period", now.toString());
        model.addAttribute("periodLabel", now.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + now.getYear());
        model.addAttribute("title", "KPI Reporting");
        model.addAttribute("subTitle", "A comprehensive overview of employee performance metrics ");
        return "kpi/report";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOP PERFORMERS – by KPI score and by appraisal
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/top-performers")
    public String topPerformers(Model model) {
        YearMonth now = YearMonth.now();

        List<TopPerformerDTO> byKpi       = kpiReportService.getTopPerformersByKpi(now, TOP_N);
        List<TopPerformerDTO> byAppraisal = kpiReportService.getTopPerformersByAppraisal(COMPANY_ID, TOP_N);

        model.addAttribute("byKpi",       byKpi);
        model.addAttribute("byAppraisal", byAppraisal);
        model.addAttribute("periodLabel", now.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + now.getYear());
        model.addAttribute("period", now.toString());
        model.addAttribute("title", "KPI Reporting");
        model.addAttribute("subTitle", "A comprehensive overview of employee performance metrics ");

        return "kpi/top-performers";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ALL EMPLOYEES SCORECARD – full KPI line-item scorecard
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/scorecard")
    public String scorecard(Model model) {
        YearMonth now = YearMonth.now();

        List<EmployeeKpiScorecardDTO> scorecard =
                kpiReportService.getAllEmployeesScorecard(now);
        model.addAttribute("scorecard", scorecard);

        // Distinct employees covered
        long employeesWithData = scorecard.stream()
                .map(EmployeeKpiScorecardDTO::getEmployeeId)
                .filter(id -> id != null)
                .distinct()
                .count();
        model.addAttribute("employeesWithData", employeesWithData);
        model.addAttribute("lineCount", scorecard.size());

        // Overall average weighted score (null-safe)
        BigDecimal avgWeighted = scorecard.stream()
                .map(s -> s.getWeightedScore() != null ? s.getWeightedScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!scorecard.isEmpty()) {
            avgWeighted = avgWeighted.divide(
                    BigDecimal.valueOf(scorecard.size()), 2, java.math.RoundingMode.HALF_UP);
        }
        model.addAttribute("avgWeightedScore", avgWeighted);

        model.addAttribute("periodLabel", now.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + now.getYear());
        model.addAttribute("period", now.toString());
        model.addAttribute("title", "KPI Reporting");
        model.addAttribute("subTitle", "A comprehensive overview of employee performance metrics ");

        return "kpi/scorecard";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APPRAISAL SUMMARY – cycle results per employee
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/appraisal")
    public String appraisalSummary(Model model) {
        List<AppraisalSummaryDTO> appraisals =
                kpiReportService.getAppraisalSummary(COMPANY_ID);
        model.addAttribute("appraisals", appraisals);

        // Average final score (null-safe)
        BigDecimal avgFinal = BigDecimal.ZERO;
        if (!appraisals.isEmpty()) {
            BigDecimal sum = appraisals.stream()
                    .map(a -> a.getFinalScore() != null ? a.getFinalScore() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgFinal = sum.divide(BigDecimal.valueOf(appraisals.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        // Outcome counts
        long needsImprovement = appraisals.stream()
                .filter(a -> "NEEDS_IMPROVEMENT".equalsIgnoreCase(
                        a.getOutcome() != null ? a.getOutcome().toString() : ""))
                .count();
        long meetsExpectations = appraisals.stream()
                .filter(a -> "MEETS_EXPECTATIONS".equalsIgnoreCase(
                        a.getOutcome() != null ? a.getOutcome().toString() : ""))
                .count();
        long exceedsExpectations = appraisals.stream()
                .filter(a -> "EXCEEDS_EXPECTATIONS".equalsIgnoreCase(
                        a.getOutcome() != null ? a.getOutcome().toString() : ""))
                .count();

        model.addAttribute("avgFinalScore",       avgFinal);
        model.addAttribute("totalAppraisals",     appraisals.size());
        model.addAttribute("needsImprovement",    needsImprovement);
        model.addAttribute("meetsExpectations",   meetsExpectations);
        model.addAttribute("exceedsExpectations", exceedsExpectations);
        model.addAttribute("title", "KPI Reporting");
        model.addAttribute("subTitle", "A comprehensive overview of employee performance metrics ");
        return "kpi/appraisal";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INDIVIDUAL EMPLOYEE SCORECARD – detail view
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/employee-scorecard/{employeeId}")
    public String employeeScorecard(@PathVariable Long employeeId, Model model) {
        YearMonth now = YearMonth.now();

        List<EmployeeKpiScorecardDTO> lines =
                kpiReportService.getEmployeeScorecard(employeeId, now);
        model.addAttribute("scorecardLines", lines);
        model.addAttribute("employeeId", employeeId);

        // Employee name from first non-null row
        String employeeName = lines.stream()
                .map(EmployeeKpiScorecardDTO::getEmployeeName)
                .filter(n -> n != null)
                .findFirst()
                .orElse("Employee #" + employeeId);
        model.addAttribute("employeeName", employeeName);

        // Totals (null-safe)
        BigDecimal totalWeightedScore = lines.stream()
                .map(l -> l.getWeightedScore() != null ? l.getWeightedScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWeight = lines.stream()
                .map(l -> l.getWeight() != null ? l.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("totalWeightedScore", totalWeightedScore);
        model.addAttribute("totalWeight",        totalWeight);
        model.addAttribute("kpiCount",           lines.size());

        model.addAttribute("periodLabel", now.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + now.getYear());
        model.addAttribute("period", now.toString());
        model.addAttribute("title", "KPI Reporting");
        model.addAttribute("subTitle", "A comprehensive overview of employee performance metrics ");

        return "kpi/employee-scorecard";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────────
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}