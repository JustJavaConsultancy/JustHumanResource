package com.justjava.humanresource.employee;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.kpi.dto.AppraisalTaskViewDTO;
import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.service.AppraisalService;
import com.justjava.humanresource.kpi.service.KpiAssignmentService;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import com.justjava.humanresource.kpi.service.KpiMeasurementService;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.service.PaySlipService;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class EmployeeController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    private SetupService setupService;

    @Autowired
    private EmployeeOnboardingService employeeOnboardingService;

    @Autowired
    private KpiAssignmentService kpiAssignmentService;

    @Autowired
    KpiMeasurementService kpiMeasurementService;

    @Autowired
    AppraisalService appraisalService;

    @Autowired
    private PayrollRunService payrollRunService;

    @Autowired
    private FlowableTaskService flowableTaskService;

    @Autowired
    private KpiDefinitionService kpiDefinitionService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PayrollSetupService payrollSetupService;

    @Autowired
    PaySlipService paySlipService;

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEES PAGE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/employees")
    public String getEmployees(Model model) {
        List<Department> departments = setupService.getAllDepartments();
        List<PayGroup> payGroups = payrollSetupService.getAllPayGroups();
        List<JobGradeResponseDTO> jobGrades = setupService.getAllJobGrades();
        jobGrades.forEach(g ->
                System.out.println("Job Grade: " + g.getSteps() + ", Description: " + g.getId()));

        List<Employee> employees = employeeOnboardingService.getAllOnboardings();
        employees.forEach(e ->
                System.out.println("Employee: " + e.getFirstName() + " " + e.getEmploymentStatus()
                        + ", Department: " + e.getDepartment().getName()));

        List<Deduction> deductions = payrollSetupService.getActiveDeductions();
        List<Allowance> allowances  = payrollSetupService.getActiveAllowances();

        model.addAttribute("deductions",  deductions);
        model.addAttribute("allowances",  allowances);
        model.addAttribute("employees",   employees);
        model.addAttribute("jobGrades",   jobGrades);
        model.addAttribute("departments", departments);
        model.addAttribute("payGroups",   payGroups);
        model.addAttribute("title",       "Employee Management");
        model.addAttribute("subTitle",    "Manage employee records, payroll, and performance data");
        return "employees/main";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ONBOARDING / CREATE
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/onboarding")
    public String startOnboarding(
            StartEmployeeOnboardingCommand command,
            @RequestParam(defaultValue = "humanResource") String initiatedBy) {

        EmployeeOnboardingResponseDTO dto =
                employeeOnboardingService.startOnboarding(command, initiatedBy);

        employeeService.changeEmploymentStatus(dto.getId(), EmploymentStatus.ACTIVE, LocalDate.now());
        return "redirect:/employees";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPDATE EMPLOYEE
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/employees/{id}")
    @ResponseBody
    public ResponseEntity<Void> updateEmployee(@PathVariable Long id,
                                               @RequestBody EmployeeDTO incomingEmployee) {
        System.out.println("Received update for employee ID: " + id + " with data: " + incomingEmployee);
        employeeOnboardingService.updateEmployee(id, incomingEmployee);
        System.out.println("Employee updated successfully for ID: " + id);
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PAY ITEMS  – GET existing assignments for one employee
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the allowances and deductions currently assigned to an employee
     * so the Pay-Items modal can pre-tick the right rows.
     *
     * Response shape:
     * {
     *   "allowances": [ ... EmployeeAllowanceResponse objects ... ],
     *   "deductions": [ ... EmployeeDeductionResponse objects ... ]
     * }
     */
    @GetMapping("/employees/{id}/pay-items")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEmployeePayItems(@PathVariable Long id) {
        List<EmployeeAllowanceResponse> allowances =
                payrollSetupService.getAllowancesForEmployee(id);
        List<EmployeeDeductionResponse> deductions =
                payrollSetupService.getDeductionsForEmployee(id);
        allowances.forEach(
                a -> System.out.println("Allowance for employee ID " + id + ": " + a));
        System.out.println("Deductions for employee ID " + id + ": " + deductions);
        return ResponseEntity.ok(Map.of(
                "allowances", allowances,
                "deductions", deductions
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PAY ITEMS  – Attach allowances to an employee
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Replaces / adds the allowance assignments for an employee.
     *
     * Request body: List of { "allowanceId": <Long>, ... }
     */
    @PostMapping("/setup/employee/{employeeId}/allowances")
    @ResponseBody
    public ResponseEntity<List<EmployeeAllowanceResponse>> attachAllowancesToEmployee(
            @PathVariable Long employeeId,
            @RequestBody List<AllowanceAttachmentRequest> requests) {

        List<EmployeeAllowanceResponse> result =
                payrollSetupService.addAllowancesToEmployee(employeeId, requests);
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PAY ITEMS  – Attach deductions to an employee
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Replaces / adds the deduction assignments for an employee.
     *
     * Request body: List of { "deductionId": <Long>, ... }
     */
    @PostMapping("/setup/employee/{employeeId}/deductions")
    @ResponseBody
    public ResponseEntity<List<EmployeeDeductionResponse>> attachDeductionsToEmployee(
            @PathVariable Long employeeId,
            @RequestBody List<DeductionAttachmentRequest> requests) {
        System.out.println("Received request to attach deductions to employee ID: " + employeeId);
        List<EmployeeDeductionResponse> result =
                payrollSetupService.addDeductionsToEmployee(employeeId, requests);
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEE DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/employee/dashboard")
    public String getEmployeeDashboard(Model model) {
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PayrollRunDTO currentPayrollRun =
                payrollRunService.getEmployeePayrollRun(loginEmployee.getId(), 1L);

        System.out.println("Latest Pay Slip: " + currentPayrollRun);
        System.out.println("Logged in employee: " + loginEmployee);

        List<PaySlipDTO> previousPaySlip =
                paySlipService.getPaySlipsByEmployee(loginEmployee.getId());

        List<HistoricProcessInstance> completedProcesses =
                flowableTaskService.getCompletedProcessInstancesForAssignee("employeeAppraisalProcess");

        List<FlowableTaskDTO> tasks = flowableTaskService.getTasksForAssignee(
                String.valueOf(loginEmployee.getId()), "employeeAppraisalProcess");

        List<AppraisalTaskViewDTO> enrichedAppraisals = new ArrayList<>();
        for (FlowableTaskDTO task : tasks) {
            Map<String, Object> variables = task.getVariables();
            if (variables.containsKey("appraisalId")) {
                Long appraisalId = Long.valueOf(variables.get("appraisalId").toString());
                EmployeeAppraisal appraisal = appraisalService.findAppraisalById(appraisalId);
                enrichedAppraisals.add(new AppraisalTaskViewDTO(task, appraisal));
            }
        }
        enrichedAppraisals.forEach(a ->
                System.out.println("Task: " + a.getTask() + ", Appraisal: " + a.getAppraisal()));

        List<EmployeeAppraisal> employeeAppraisals =
                appraisalService.findAppraisalByEmployeeID(loginEmployee.getId());

        model.addAttribute("tasks",            tasks);
        model.addAttribute("employeeAppraisals", employeeAppraisals);
        model.addAttribute("appraisalMap",
                employeeAppraisals.stream()
                        .collect(Collectors.toMap(EmployeeAppraisal::getId, ea -> ea)));
        model.addAttribute("previousPaySlip", previousPaySlip);
        model.addAttribute("employee",        employee);
        model.addAttribute("latestPaySlip",   currentPayrollRun);
        model.addAttribute("title",           "Employee Dashboard");
        model.addAttribute("subTitle",
                "View your profile, performance metrics, and payroll information");
        return "employees/dashboard";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEE PROFILE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("employee/profile")
    public String getEmployeeProfile(Model model) {
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PayrollRun currentPayrollRun =
                paySlipService.getEmployeeCurrentPayrollRun(1L, loginEmployee.getId());

        model.addAttribute("employee",     employee);
        model.addAttribute("latestPaySlip", currentPayrollRun);
        model.addAttribute("title",        "Employee Profile");
        model.addAttribute("subTitle",
                "View and update your personal information, job details, and performance data");
        return "employees/profile";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MISC EMPLOYEE PAGES
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("employee/promotions")
    public String getPromotions(Model model) {
        model.addAttribute("title",    "Promotions");
        model.addAttribute("subTitle", "View your promotion history and upcoming opportunities");
        return "employees/promotions";
    }

    @GetMapping("employee/payroll")
    public String getPayroll(Model model) {
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        PayrollRunDTO latestPaySlip =
                payrollRunService.getEmployeePayrollRun(loginEmployee.getId(), 1L);
        List<PaySlipDTO> previousPaySlip =
                paySlipService.getPaySlipsByEmployee(loginEmployee.getId());
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());

        model.addAttribute("previousPaySlip", previousPaySlip);
        model.addAttribute("latestPaySlip",   latestPaySlip);
        model.addAttribute("employee",        employee);
        model.addAttribute("title",           "Payroll");
        model.addAttribute("subTitle",        "View your pay stubs, tax information, and benefits");
        return "employees/payroll";
    }

    @GetMapping("employee/leave")
    public String getLeave(Model model) {
        model.addAttribute("title",    "Leave Management");
        model.addAttribute("subTitle",
                "View your leave balance, request time off, and track your leave history");
        return "employees/leave";
    }

    @GetMapping("employee/performance")
    public String getPerformance(Model model) {
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);

        List<HistoricProcessInstance> completedProcesses =
                flowableTaskService.getCompletedProcessInstancesForAssignee("employeeAppraisalProcess");

        List<FlowableTaskDTO> tasks = flowableTaskService.getTasksForAssignee(
                String.valueOf(loginEmployee.getId()), "employeeAppraisalProcess");

        List<AppraisalTaskViewDTO> enrichedAppraisals = new ArrayList<>();
        for (FlowableTaskDTO task : tasks) {
            Map<String, Object> variables = task.getVariables();
            if (variables.containsKey("appraisalId")) {
                Long appraisalId = Long.valueOf(variables.get("appraisalId").toString());
                EmployeeAppraisal appraisal = appraisalService.findAppraisalById(appraisalId);
                enrichedAppraisals.add(new AppraisalTaskViewDTO(task, appraisal));
            }
        }
        enrichedAppraisals.forEach(a ->
                System.out.println("Task: " + a.getTask() + ", Appraisal: " + a.getAppraisal()));

        List<EmployeeAppraisal> employeeAppraisals =
                appraisalService.findAppraisalByEmployeeID(loginEmployee.getId());

        model.addAttribute("tasks",            tasks);
        model.addAttribute("employeeAppraisals", employeeAppraisals);
        model.addAttribute("appraisalMap",
                employeeAppraisals.stream()
                        .collect(Collectors.toMap(EmployeeAppraisal::getId, ea -> ea)));
        model.addAttribute("title",    "Performance Management");
        model.addAttribute("subTitle",
                "View your performance reviews, set goals, and track your progress");
        return "employees/kpi";
    }

    @PostMapping("/employee/self-review")
    public String submitSelfReview(@RequestParam String taskId,
                                   @RequestParam Map<String, Object> formParams) {
        System.out.println("Finalizing appraisal with ID: " + taskId
                + " and form parameters: " + formParams);
        formParams.put("selfComplete", true);
        flowableTaskService.completeTask(taskId, formParams);
        return "redirect:/employee/performance";
    }

    @GetMapping("employee/documents")
    public String getDocuments(Model model) {
        model.addAttribute("title",    "Document Management");
        model.addAttribute("subTitle",
                "View and manage your important documents such as contracts, certifications, and performance reviews");
        return "employees/documents";
    }

    @PostMapping("/add-emergency-contact")
    public String saveEmployee(EmployeeDTO dto) {
        employeeService.updateEmployee(dto.getId(), dto);
        return "redirect:/employee/profile";
    }

    @PostMapping("/update-personal-info")
    public String updatePersonalInfo(@ModelAttribute EmployeeDTO dto) {
        employeeService.updatePersonalInfo(dto.getId(), dto);
        return "redirect:/employee/profile";
    }

    @PostMapping("/update/bank-info")
    public String updateBankInfo(@ModelAttribute EmployeeDTO dto) {
        employeeService.updateBankDetails(dto.getId(), dto);
        return "redirect:/employee/profile";
    }
}