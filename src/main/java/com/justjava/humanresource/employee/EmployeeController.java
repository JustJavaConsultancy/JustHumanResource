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
import com.justjava.humanresource.payroll.entity.PaySlipDTO;
import com.justjava.humanresource.payroll.service.PaySlipService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
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
    private FlowableTaskService flowableTaskService;

    @Autowired
    private KpiDefinitionService kpiDefinitionService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PayrollSetupService payrollSetupService;

    @Autowired
    PaySlipService paySlipService;

    @GetMapping("/employees")
    public String getEmployees(Model model) {
        List<Department> departments = setupService.getAllDepartments();
        List<PayGroup> payGroups = payrollSetupService.getAllPayGroups();
        List<JobGradeResponseDTO> jobGrades = setupService.getAllJobGrades();
        jobGrades.forEach(
                jobGrade -> System.out.println("Job Grade: " + jobGrade.getSteps() + ", Description: " + jobGrade.getId())
        );
        List<Employee> employees = employeeOnboardingService.getAllOnboardings();
        employees.forEach(
                employee -> System.out.println("Employee: " + employee.getFirstName() + " " + employee.getEmploymentStatus() + ", Department: " + employee.getDepartment().getName())
        );
        model.addAttribute("employees", employees);
        model.addAttribute("jobGrades", jobGrades);
        model.addAttribute("departments", departments);
        model.addAttribute("payGroups", payGroups);
        model.addAttribute("title","Employee Management");
        model.addAttribute("subTitle","Manage employee records, payroll, and performance data");
        return "employees/main";
    }
    @PostMapping("/onboarding")
    public String startOnboarding(
            StartEmployeeOnboardingCommand command,
            @RequestParam(defaultValue = "humanResource") String initiatedBy) {
        EmployeeOnboardingResponseDTO employeeOnboardingResponseDTO=employeeOnboardingService.startOnboarding(
                command,
                initiatedBy
        );
        employeeService.changeEmploymentStatus(
                employeeOnboardingResponseDTO.getId(),
                EmploymentStatus.ACTIVE, LocalDate.now());

        return "redirect:/employees";
    }
    @GetMapping("/employee/dashboard")
    public String getEmployeeDashboard(Model model) {
        model.addAttribute("title", "Employee Dashboard");
        model.addAttribute("subTitle", "View your profile, performance metrics, and payroll information");
        return "employees/dashboard";
    }
    @GetMapping("employee/profile")
    public String getEmployeeProfile(Model model) {
        String email = (String) authenticationManager.get("email");
        // implement your own logic
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PaySlipDTO latestPaySlip = paySlipService.getCurrentPeriodPaySlipForEmployee(1l,loginEmployee.getId());
        System.out.println("Latest Pay Slip: " + latestPaySlip);
        System.out.println("Logged in employee: " + loginEmployee);
        model.addAttribute("employee", employee);
        model.addAttribute("latestPaySlip", latestPaySlip);
        model.addAttribute("title", "Employee Profile");
        model.addAttribute("subTitle", "View and update your personal information, job details, and performance data");
        return "employees/profile";
    }
    @GetMapping("employee/promotions")
    public String getPromotions(Model model){
        model.addAttribute("title", "Promotions");
        model.addAttribute("subTitle", "View your promotion history and upcoming opportunities");
        return "employees/promotions";
    }
    @GetMapping("employee/payroll")
    public String getPayroll(Model model){
        String email = (String) authenticationManager.get("email");
        // implement your own logic
        Employee loginEmployee = employeeService.getByEmail(email);
        PaySlipDTO latestPaySlip = paySlipService.getCurrentPeriodPaySlipForEmployee(1l,loginEmployee.getId());

        List<PaySlipDTO> previousPaySlip = paySlipService.getPaySlipsByEmployee(loginEmployee.getId());
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        System.out.println("Latest Pay Slip: " );
        model.addAttribute("previousPaySlip", previousPaySlip);
        model.addAttribute("latestPaySlip", latestPaySlip);
        model.addAttribute("employee", employee);
        model.addAttribute("title", "Payroll");
        model.addAttribute("subTitle", "View your pay stubs, tax information, and benefits");

        return "employees/payroll";
    }
    @GetMapping("employee/leave")
    public String getLeave(Model model){
        model.addAttribute("title", "Leave Management");
        model.addAttribute("subTitle", "View your leave balance, request time off, and track your leave history");
        return "employees/leave";
    }
    @GetMapping("employee/performance")
    public String getPerformance(Model model){
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        // 🔹 COMPLETED PROCESSES
        List<HistoricProcessInstance> completedProcesses =
                flowableTaskService.getCompletedProcessInstancesForAssignee(
                        "employeeAppraisalProcess"
                );

        // 🔹 ACTIVE TASKS
        List<FlowableTaskDTO> tasks =
                flowableTaskService.getTasksForAssignee(
                        String.valueOf(loginEmployee.getId()),
                        "employeeAppraisalProcess"
                );
        List<AppraisalTaskViewDTO> enrichedAppraisals = new ArrayList<>();

        for (FlowableTaskDTO task : tasks) {

            Map<String, Object> variables = task.getVariables();

            if (variables.containsKey("appraisalId")) {

                Long appraisalId =
                        Long.valueOf(variables.get("appraisalId").toString());

                EmployeeAppraisal appraisal =
                        appraisalService.findAppraisalById(appraisalId);
                enrichedAppraisals.add(
                        new AppraisalTaskViewDTO(task, appraisal)
                );
            }
        }
        enrichedAppraisals.forEach(
                appraisal -> System.out.println("Task: " + appraisal.getTask()+ ", Appraisal: " + appraisal.getAppraisal())
        );
        List<EmployeeAppraisal> employeeAppraisals = appraisalService.findAppraisalByEmployeeID(loginEmployee.getId());
        // existing
        model.addAttribute("tasks", tasks);
        model.addAttribute("employeeAppraisals", employeeAppraisals);

// add this
        model.addAttribute("appraisalMap",
                employeeAppraisals.stream()
                        .collect(Collectors.toMap(EmployeeAppraisal::getId, ea -> ea))
        );
        model.addAttribute("title", "Performance Management");
        model.addAttribute("subTitle", "View your performance reviews, set goals, and track your progress");
        return "employees/kpi";
    }
    @PostMapping("/employee/self-review")
    public String submitSelfReview(@RequestParam String taskId,
                                   @RequestParam Map<String, Object> formParams) {
        System.out.println("Finalizing appraisal with ID: " + taskId + " and form parameters: " + formParams);
        formParams.put("selfComplete", true);

        flowableTaskService.completeTask(taskId,formParams);

        return "redirect:/employee/performance";
    }
    @GetMapping("employee/documents")
    public String getDocuments(Model model){
        model.addAttribute("title", "Document Management");
        model.addAttribute("subTitle", "View and manage your important documents such as contracts, certifications, and performance reviews");
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
