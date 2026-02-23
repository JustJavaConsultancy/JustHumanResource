package com.justjava.humanresource.kpi;

import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.dto.KpiAssignmentResponseDTO;
import com.justjava.humanresource.hr.dto.KpiBulkAssignmentRequestDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.kpi.controller.AppraisalController;
import com.justjava.humanresource.kpi.dto.AppraisalTaskViewDTO;
import com.justjava.humanresource.kpi.dto.EmployeeAppraisalDTO;
import com.justjava.humanresource.kpi.entity.*;
import com.justjava.humanresource.kpi.service.AppraisalService;
import com.justjava.humanresource.kpi.service.KpiAssignmentService;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import com.justjava.humanresource.kpi.service.KpiMeasurementService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class KpiController {
    @Autowired
    private KpiAssignmentService kpiAssignmentService;

    @Autowired
    KpiMeasurementService kpiMeasurementService;

    @Autowired
    AppraisalService appraisalService;

    @Autowired
    private KpiDefinitionService kpiDefinitionService;

    @Autowired
    private EmployeeOnboardingService employeeOnboardingService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private FlowableTaskService flowableTaskService;

    @GetMapping("/kpi")
    public String attendancePage(Model model) {
        List<KpiDefinition> kpiDefinitions = kpiDefinitionService.getAll();
        List<Employee> employees = employeeOnboardingService.getAllOnboardings();
        List<JobGradeResponseDTO> jobGrades = setupService.getAllJobGrades();

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
        model.addAttribute("assignmentsByEmployee", assignmentsByEmployee.size());
        model.addAttribute("assignmentsByJobStep", assignmentsByJobStep.size());
        model.addAttribute("totalAssignments",assignmentsByEmployee.size() + assignmentsByJobStep.size());
        model.addAttribute("definitionSize", kpiDefinitions.size());
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
        List<KpiMeasurementResponseDTO> measurements = kpiMeasurementService.getAllEffectiveMeasurements(YearMonth.now());
        model.addAttribute("measurements", measurements);
        return "kpi/fragment/kpi-measurements-table";
    }
    @PostMapping("/kpi/measurements")
    public String submitKpiMeasurements(KpiBulkMeasurementRequestDTO request, Model model) {
        System.out.println("Received KPI Measurement Submission: " + request);
        kpiMeasurementService.recordBulkMeasurements(request);
        System.out.println("Successfully recorded KPI measurements for employee ID: " + request.getEmployeeId());

        List<KpiMeasurementResponseDTO> measurements = kpiMeasurementService.getAllEffectiveMeasurements(YearMonth.now());
        model.addAttribute("measurements", measurements);
        return "kpi/fragment/kpi-measurements-table";
    }
    @GetMapping("/fragments/stats-cards")
        public String getStatsCards(Model model) {
        List<KpiDefinition> kpiDefinitions = kpiDefinitionService.getAll();
        List<Employee> employees = employeeOnboardingService.getAllOnboardings();
        List<JobGradeResponseDTO> jobGrades = setupService.getAllJobGrades();

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
        model.addAttribute("assignmentsByEmployee", assignmentsByEmployee.size());
        model.addAttribute("assignmentsByJobStep", assignmentsByJobStep.size());
        model.addAttribute("totalAssignments",assignmentsByEmployee.size() + assignmentsByJobStep.size());
        model.addAttribute("definitionSize", kpiDefinitions.size());
            // Return the fragment (the part inside th:fragment="stats-cards")
            return "kpi/fragment/stats-cards :: stats-cards";
    }
    @GetMapping("/fragments/kpi-appraisal")
    public String getAppraisalFragment(Model model) {

        // 🔹 COMPLETED PROCESSES
        List<HistoricProcessInstance> completedProcesses =
                flowableTaskService.getCompletedProcessInstancesForAssignee(
                        "employeeAppraisalProcess"
                );

        // 🔹 ACTIVE TASKS
        List<FlowableTaskDTO> tasks =
                flowableTaskService.getTasksForAssignee(
                        "mgr",
                        "employeeAppraisalProcess"
                );

        List<AppraisalTaskViewDTO> managerPending = new ArrayList<>();
        List<AppraisalTaskViewDTO> selfPending = new ArrayList<>();
        List<AppraisalTaskViewDTO> completedAppraisals = new ArrayList<>();

        // =========================
        // ACTIVE TASK PROCESSING
        // =========================
        for (FlowableTaskDTO task : tasks) {

            Map<String, Object> variables = task.getVariables();
            if (!variables.containsKey("appraisalId")) continue;

            Long appraisalId =
                    Long.valueOf(variables.get("appraisalId").toString());

            EmployeeAppraisal appraisal =
                    appraisalService.findAppraisalById(appraisalId);

            Boolean managerComplete =
                    Boolean.parseBoolean(
                            String.valueOf(variables.get("managerComplete"))
                    );

            Boolean selfComplete =
                    Boolean.parseBoolean(
                            String.valueOf(variables.get("selfComplete"))
                    );

            AppraisalTaskViewDTO dto =
                    new AppraisalTaskViewDTO(task, appraisal);

            if (!managerComplete) {
                managerPending.add(dto);
            }

            if (!selfComplete) {
                selfPending.add(dto);
            }
        }

        // =========================
        // COMPLETED PROCESS HANDLING
        // =========================
        for (HistoricProcessInstance process : completedProcesses) {

            Map<String, Object> variables = process.getProcessVariables();

            if (variables == null || !variables.containsKey("appraisalId"))
                continue;

            Long appraisalId =
                    Long.valueOf(variables.get("appraisalId").toString());

            EmployeeAppraisal appraisal =
                    appraisalService.findAppraisalById(appraisalId);

            // For completed, task can be null or create another DTO if needed
            completedAppraisals.add(
                    new AppraisalTaskViewDTO(null, appraisal)
            );
        }

        model.addAttribute("managerPendingAppraisals", managerPending);
        model.addAttribute("selfPendingAppraisals", selfPending);
        model.addAttribute("completedAppraisals", completedAppraisals);

        return "kpi/fragment/appraisal-fragment :: appraisal-content";
    }
    @PostMapping("/finalize-appraisal")
    public String finalizeAppraisal(@RequestParam String taskId,
            @RequestParam Map<String, Object> formParams,
                                    Model model) {
        System.out.println("Finalizing appraisal with ID: " + taskId + " and form parameters: " + formParams);
        formParams.put("managerComplete", true);

        flowableTaskService.completeTask(taskId,formParams);

        // 🔹 COMPLETED PROCESSES
        List<HistoricProcessInstance> completedProcesses =
                flowableTaskService.getCompletedProcessInstancesForAssignee(
                        "employeeAppraisalProcess"
                );

        // 🔹 ACTIVE TASKS
        List<FlowableTaskDTO> tasks =
                flowableTaskService.getTasksForAssignee(
                        "mgr",
                        "employeeAppraisalProcess"
                );

        List<AppraisalTaskViewDTO> managerPending = new ArrayList<>();
        List<AppraisalTaskViewDTO> selfPending = new ArrayList<>();
        List<AppraisalTaskViewDTO> completedAppraisals = new ArrayList<>();

        // =========================
        // ACTIVE TASK PROCESSING
        // =========================
        for (FlowableTaskDTO task : tasks) {

            Map<String, Object> variables = task.getVariables();
            if (!variables.containsKey("appraisalId")) continue;

            Long appraisalId =
                    Long.valueOf(variables.get("appraisalId").toString());

            EmployeeAppraisal appraisal =
                    appraisalService.findAppraisalById(appraisalId);

            Boolean managerComplete =
                    Boolean.parseBoolean(
                            String.valueOf(variables.get("managerComplete"))
                    );

            Boolean selfComplete =
                    Boolean.parseBoolean(
                            String.valueOf(variables.get("selfComplete"))
                    );

            AppraisalTaskViewDTO dto =
                    new AppraisalTaskViewDTO(task, appraisal);

            if (!managerComplete) {
                managerPending.add(dto);
            }

            if (!selfComplete) {
                selfPending.add(dto);
            }
        }

        // =========================
        // COMPLETED PROCESS HANDLING
        // =========================
        for (HistoricProcessInstance process : completedProcesses) {

            Map<String, Object> variables = process.getProcessVariables();

            if (variables == null || !variables.containsKey("appraisalId"))
                continue;

            Long appraisalId =
                    Long.valueOf(variables.get("appraisalId").toString());

            EmployeeAppraisal appraisal =
                    appraisalService.findAppraisalById(appraisalId);

            // For completed, task can be null or create another DTO if needed
            completedAppraisals.add(
                    new AppraisalTaskViewDTO(null, appraisal)
            );
        }

        model.addAttribute("managerPendingAppraisals", managerPending);
        model.addAttribute("selfPendingAppraisals", selfPending);
        model.addAttribute("completedAppraisals", completedAppraisals);

        return "kpi/fragment/appraisal-fragment :: appraisal-content";
        }
}
