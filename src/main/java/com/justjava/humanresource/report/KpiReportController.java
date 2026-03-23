package com.justjava.humanresource.report;

import com.justjava.humanresource.kpi.report.service.KPIReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class KpiReportController {
    @Autowired
    KPIReportService kpiReportService;

    @GetMapping("/reporting/kpis")
    public String kpiReporting() {
        System.out.println("Generating KPI report...");
        System.out.println("This is the first report" + kpiReportService.getDepartmentKpiReport(YearMonth.now()));
        System.out.println("This is the third report" + kpiReportService.getTopPerformersByKpi(YearMonth.now(), 10));
        System.out.println("This is the fourth report" + kpiReportService.getTopPerformersByAppraisal(1L, 10));
        System.out.println("This is the fifth report" + kpiReportService.getAllEmployeesScorecard(YearMonth.now()));
        System.out.println("This is the appraisal summary report" + kpiReportService.getAppraisalSummary(1L));
        System.out.println("This is the dashboard report" + kpiReportService.getKpiDashboard(YearMonth.now(), 5, 5));
        System.out.println("This is the employee scorecard report" + kpiReportService.getEmployeeScorecard(1L, YearMonth.now()));
        return "kpi/report";
    }
}
