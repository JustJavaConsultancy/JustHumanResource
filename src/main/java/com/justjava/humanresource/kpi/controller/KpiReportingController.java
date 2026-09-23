package com.justjava.humanresource.kpi.controller;

import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.dto.KpiCompletionStatusDTO;
import com.justjava.humanresource.kpi.dto.KpiCyclePerformanceReportDTO;
import com.justjava.humanresource.kpi.dto.KpiDepartmentPerformanceDTO;
import com.justjava.humanresource.kpi.dto.KpiPerspectivePerformanceDTO;
import com.justjava.humanresource.kpi.dto.KpiReportFilterDTO;
import com.justjava.humanresource.kpi.dto.KpiRubricDistributionDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardMonitoringDTO;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import com.justjava.humanresource.kpi.service.KpiReportingService;
import com.justjava.humanresource.kpi.service.KpiScorecardTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequiredArgsConstructor
@PreAuthorize("@kpiAuthorization.canManageKpi(authentication)")
public class KpiReportingController {

    private final KpiReportingService reportingService;
    private final KpiScorecardTemplateService templateService;
    private final AppraisalCycleRepository cycleRepository;
    private final DepartmentRepository departmentRepository;
    private final JobStepRepository jobStepRepository;
    private final EmployeeRepository employeeRepository;

    @GetMapping("/kpi/scorecards/dashboard")
    public String scorecardDashboard(Model model) {
        model.addAttribute("monitoring", reportingService.getMonitoringDashboard());
        model.addAttribute("title", "KPI Scorecard Dashboard");
        model.addAttribute("subTitle", "Template health, assignments, and review queues");
        return "kpi/scorecard-dashboard";
    }

    @GetMapping("/kpi/reports/performance")
    public String performanceDashboard(Model model, KpiReportFilterDTO filter) {
        model.addAttribute("filter", filter);
        model.addAttribute("report", reportingService.getPerformanceReport(filter));
        model.addAttribute("departmentsReport", reportingService.getDepartmentPerformance(filter));
        model.addAttribute("jobStepReport", reportingService.getJobStepPerformance(filter));
        model.addAttribute("perspectives", reportingService.getPerspectivePerformance(filter));
        model.addAttribute("rubrics", reportingService.getRubricDistribution(filter));
        model.addAttribute("completion", reportingService.getCompletionStatus(filter));
        model.addAttribute("cycles", cycleRepository.findAll());
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("jobSteps", jobStepRepository.findAllWithJobGrade());
        model.addAttribute("employees", employeeRepository.findAll());
        model.addAttribute("templates", templateService.getManageableTemplates());
        model.addAttribute("title", "KPI Performance Reports");
        model.addAttribute("subTitle", "Executive score trends and completion analytics");
        return "kpi/performance-reports";
    }

    @ResponseBody
    @GetMapping("/api/kpi/scorecards/monitoring")
    public KpiScorecardMonitoringDTO monitoring() {
        return reportingService.getMonitoringDashboard();
    }

    @ResponseBody
    @GetMapping("/api/kpi/reports/performance")
    public KpiCyclePerformanceReportDTO performance(KpiReportFilterDTO filter) {
        return reportingService.getPerformanceReport(filter);
    }

    @ResponseBody
    @GetMapping("/api/kpi/reports/departments")
    public List<KpiDepartmentPerformanceDTO> departments(KpiReportFilterDTO filter) {
        return reportingService.getDepartmentPerformance(filter);
    }

    @ResponseBody
    @GetMapping("/api/kpi/reports/perspectives")
    public List<KpiPerspectivePerformanceDTO> perspectives(KpiReportFilterDTO filter) {
        return reportingService.getPerspectivePerformance(filter);
    }

    @ResponseBody
    @GetMapping("/api/kpi/reports/rubrics")
    public List<KpiRubricDistributionDTO> rubrics(KpiReportFilterDTO filter) {
        return reportingService.getRubricDistribution(filter);
    }

    @ResponseBody
    @GetMapping("/api/kpi/reports/completion")
    public List<KpiCompletionStatusDTO> completion(KpiReportFilterDTO filter) {
        return reportingService.getCompletionStatus(filter);
    }

    @GetMapping("/api/kpi/reports/export")
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam(defaultValue = "executive") String type,
            @RequestParam(defaultValue = "csv") String format,
            KpiReportFilterDTO filter
    ) {
        byte[] bytes = reportingService.export(type, format, filter);
        boolean pdf = "pdf".equalsIgnoreCase(format);
        String extension = pdf ? "pdf" : "csv";
        MediaType mediaType = pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("text/csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kpi-" + type + "-report." + extension + "\"")
                .contentType(mediaType)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
