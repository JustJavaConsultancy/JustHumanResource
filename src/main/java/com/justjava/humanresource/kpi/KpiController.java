package com.justjava.humanresource.kpi;

import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.dto.KpiAssignmentResponseDTO;
import com.justjava.humanresource.hr.dto.KpiBulkAssignmentRequestDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.service.KpiAssignmentService;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class KpiController {
    @Autowired
    private KpiAssignmentService kpiAssignmentService;

    @Autowired
    private KpiDefinitionService kpiDefinitionService;

    @Autowired
    private EmployeeOnboardingService employeeOnboardingService;
    @Autowired
    private SetupService setupService;
    @GetMapping("/kpi")
    public String attendancePage(Model model) {
        List<KpiDefinition> kpiDefinitions = kpiDefinitionService.getAll();
        List<Employee> employees = employeeOnboardingService.getAllOnboardings();
        List<JobGradeResponseDTO> jobGrades = setupService.getAllJobGrades();
        model.addAttribute("jobGrades", jobGrades);
        model.addAttribute("employees", employees);
        model.addAttribute("kpiDefinitions", kpiDefinitions);
        model.addAttribute("title","KPI Management");
        model.addAttribute("subTitle","Track and analyze employee performance metrics");
        return "kpi/main";
    }
    @GetMapping("/fragments/kpi-assignments")
    public String getKpiAssignmentsFragment(Model model) {
        List<KpiAssignment> assignments = kpiAssignmentService.getAllAssignments();

        System.out.println("=================================");
        return "kpi/fragment/kpi-assignments-fragment";
    }
    @PostMapping("/kpi/definition")
    public String kpiDefinition(KpiDefinition kpi) {
       kpiDefinitionService.create(kpi);
         return "redirect:/kpi";
    }
    @PostMapping("/kpi/assign")
    public String assignKpi(KpiBulkAssignmentRequestDTO request) {
        kpiAssignmentService.bulkAssign(request);
        System.out.println("=================================");
        System.out.println("Received KPI Assignment Request: " + request);

        // Return the fragment to reload the assignments tab
        return "kpi/fragment/kpi-assignments-fragment";
    }
}
