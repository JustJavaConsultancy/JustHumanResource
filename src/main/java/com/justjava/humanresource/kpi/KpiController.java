package com.justjava.humanresource.kpi;

import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.dto.KpiAssignmentResponseDTO;
import com.justjava.humanresource.hr.dto.KpiBulkAssignmentRequestDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.entity.KpiBulkMeasurementRequestDTO;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import com.justjava.humanresource.kpi.service.KpiAssignmentService;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import com.justjava.humanresource.kpi.service.KpiMeasurementService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class KpiController {
    @Autowired
    private KpiAssignmentService kpiAssignmentService;

    @Autowired
    KpiMeasurementService kpiMeasurementService;

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

        // Debug: print all assignments (original code, but safe now)
        assignments.forEach(assignment -> {
            JobStep step = assignment.getJobStep();
            System.out.println(step != null ? step : "null");
        });

        // Filter out assignments with null jobStep for grouping by job step
        List<JobStep> uniqueJobSteps = assignments.stream()
                .map(KpiAssignment::getJobStep)
                .filter(Objects::nonNull)               // exclude null job steps
                .distinct()
                .collect(Collectors.toList());

        System.out.println("Unique Job Steps:");

        Map<JobStep, List<KpiAssignmentResponseDTO>> assignmentsByJobStep = new LinkedHashMap<>();
        for (JobStep jobStep : uniqueJobSteps) {
            List<KpiAssignmentResponseDTO> jobStepKpis = kpiAssignmentService.getAssignmentsForEmployee(jobStep.getId());
            assignmentsByJobStep.put(jobStep, jobStepKpis);
        }

        // Get unique employees (excluding null)
        List<Employee> uniqueEmployees = assignments.stream()
                .map(KpiAssignment::getEmployee)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Build map of employee -> assignments
        Map<Employee, List<KpiAssignmentResponseDTO>> assignmentsByEmployee = new LinkedHashMap<>();
        for (Employee employee : uniqueEmployees) {
            List<KpiAssignmentResponseDTO> employeeKpis = kpiAssignmentService.getAssignmentsForEmployee(employee.getId());
            System.out.println("KpI's" + employeeKpis.size());
            assignmentsByEmployee.put(employee, employeeKpis);
        }

        // Debug output (safe now)
        assignmentsByEmployee.forEach((employee, kpis) -> {
            System.out.println("Employee: " + employee.getFirstName() + " " + employee.getLastName());
            kpis.forEach(kpi -> System.out.println("  KPI: " + kpi.getName()));
        });

        assignmentsByJobStep.forEach(
                (jobStep, kpis) -> {
                    System.out.println("Job Step: " + jobStep.getJobGrade().getName());
                    kpis.forEach(kpi -> System.out.println("  KPI: " + kpi.getName()));
                }
        );

        model.addAttribute("assignmentsByEmployee", assignmentsByEmployee);
        model.addAttribute("assignmentsByJobStep", assignmentsByJobStep);
        model.addAttribute("employmentSize", assignmentsByEmployee.size()+ assignmentsByJobStep.size());
        return "kpi/fragment/kpi-assignments-fragment";
    }
    @PostMapping("/kpi/definition")
    public String kpiDefinition(KpiDefinition kpi) {
       kpiDefinitionService.create(kpi);
         return "redirect:/kpi";
    }
    @PostMapping("/kpi/assign")
    public String assignKpi(KpiBulkAssignmentRequestDTO request,Model model) {
        kpiAssignmentService.bulkAssign(request);
        System.out.println("=================================");
        System.out.println("Received KPI Assignment Request: " + request);
        List<KpiAssignment> assignments = kpiAssignmentService.getAllAssignments();

        // Debug: print all assignments (original code, but safe now)
        assignments.forEach(assignment -> {
            JobStep step = assignment.getJobStep();
            System.out.println(step != null ? step : "null");
        });

        // Filter out assignments with null jobStep for grouping by job step
        List<JobStep> uniqueJobSteps = assignments.stream()
                .map(KpiAssignment::getJobStep)
                .filter(Objects::nonNull)               // exclude null job steps
                .distinct()
                .collect(Collectors.toList());

        System.out.println("Unique Job Steps:");

        Map<JobStep, List<KpiAssignmentResponseDTO>> assignmentsByJobStep = new LinkedHashMap<>();
        for (JobStep jobStep : uniqueJobSteps) {
            List<KpiAssignmentResponseDTO> jobStepKpis = kpiAssignmentService.getAssignmentsForEmployee(jobStep.getId());
            assignmentsByJobStep.put(jobStep, jobStepKpis);
        }

        // Get unique employees (excluding null)
        List<Employee> uniqueEmployees = assignments.stream()
                .map(KpiAssignment::getEmployee)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Build map of employee -> assignments
        Map<Employee, List<KpiAssignmentResponseDTO>> assignmentsByEmployee = new LinkedHashMap<>();
        for (Employee employee : uniqueEmployees) {
            List<KpiAssignmentResponseDTO> employeeKpis = kpiAssignmentService.getAssignmentsForEmployee(employee.getId());
            assignmentsByEmployee.put(employee, employeeKpis);
        }

        // Debug output (safe now)
        assignmentsByEmployee.forEach((employee, kpis) -> {
            System.out.println("Employee: " + employee.getFirstName() + " " + employee.getLastName());
            kpis.forEach(kpi -> System.out.println("  KPI: " + kpi.getName()));
        });

        assignmentsByJobStep.forEach(
                (jobStep, kpis) -> {
                    System.out.println("Job Step: " + jobStep.getJobGrade().getName());
                    kpis.forEach(kpi -> System.out.println("  KPI: " + kpi.getName()));
                }
        );

        model.addAttribute("assignmentsByEmployee", assignmentsByEmployee);
        model.addAttribute("assignmentsByJobStep", assignmentsByJobStep);
        model.addAttribute("employmentSize", assignmentsByEmployee.size()+ assignmentsByJobStep.size());
        // Return the fragment to reload the assignments tab
        return "kpi/fragment/kpi-assignments-fragment";
    }
    @GetMapping("/kpi/measurements/form-items")
    public String getMeasurementFormItems(@RequestParam Long employeeId, Model model) {
        List<KpiAssignmentResponseDTO> kpiDefinition = kpiAssignmentService.getAssignmentsForEmployee(employeeId);
        System.out.println("Received request for measurement form items for employee ID: " + employeeId);

        kpiDefinition.forEach(
                kpi -> System.out.println("KPI: " + kpi.getName() )
        );
        model.addAttribute("kpiDefinition", kpiDefinition);
        return "kpi/fragment/measurement-form-items-fragment :: kpi-measurement-items";
    }
    @GetMapping("/fragments/kpi-measurements-table")
    public String getKpiMeasurementsTable(Model model) {
        List<KpiMeasurement> measurements = kpiMeasurementService.getAllMeasurements();
        model.addAttribute("measurements", measurements);
        return "kpi/fragment/kpi-measurements-table";
    }
    @PostMapping("/kpi/measurements")
    public String submitKpiMeasurements(KpiBulkMeasurementRequestDTO request, Model model) {
        System.out.println("Received KPI Measurement Submission: " + request);
        kpiMeasurementService.recordBulkMeasurements(request);
        System.out.println("Successfully recorded KPI measurements for employee ID: " + request.getEmployeeId());

        List<KpiMeasurement> measurements = kpiMeasurementService.getAllMeasurements();
        model.addAttribute("measurements", measurements);
        return "kpi/fragment/kpi-measurements-table";
    }
}
