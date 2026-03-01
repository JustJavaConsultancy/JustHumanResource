package com.justjava.humanresource.mobile;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.kpi.dto.AppraisalTaskViewDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mobile")
public class MobileEmployeeController {


    @Autowired
    private SetupService setupService;

    @Autowired
    private EmployeeOnboardingService employeeOnboardingService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PayrollSetupService payrollSetupService;

    @Autowired
    AuthenticationManager authenticationManager;


    @Autowired
    AppraisalService appraisalService;

    @Autowired
    private FlowableTaskService flowableTaskService;

    @Autowired
    private PaySlipService paySlipService;


    @GetMapping("/employees")
    public String getMobileEmployees(Model model) {
        model.addAttribute("title", "Employee Management");
        model.addAttribute("subTitle", "Manage employee records and data");
        return "mobile/main";
    }

    @PostMapping("/onboarding")
    public String startMobileOnboarding(
            StartEmployeeOnboardingCommand command,
            @RequestParam(defaultValue = "mobileHR") String initiatedBy) {
        EmployeeOnboardingResponseDTO employeeOnboardingResponseDTO = employeeOnboardingService.startOnboarding(
                command,
                initiatedBy
        );
        employeeService.changeEmploymentStatus(
                employeeOnboardingResponseDTO.getId(),
                EmploymentStatus.ACTIVE, LocalDate.now());

        return "redirect:/mobile/employees";
    }

    @GetMapping("/employee/dashboard")
    public String getMobileEmployeeDashboard(Model model) {
        String email = (String) authenticationManager.get("email");
        // implement your own logic
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PaySlipDTO latestPaySlip = paySlipService.getCurrentPeriodPaySlipForEmployee(1l,loginEmployee.getId());
        System.out.println("Latest Pay Slip: " + latestPaySlip);
        System.out.println("Logged in employee: " + loginEmployee);
        List<PaySlipDTO> previousPaySlip = paySlipService.getPaySlipsByEmployee(loginEmployee.getId());
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
        model.addAttribute("previousPaySlip", previousPaySlip);
        model.addAttribute("employee", employee);
        model.addAttribute("latestPaySlip", latestPaySlip);
        model.addAttribute("title", "Dashboard");
        model.addAttribute("subTitle", "Your personal overview");
        return "mobile/dashboard";
    }

    @GetMapping("/employee/profile")
    public String getMobileEmployeeProfile(Model model) {
        String email = (String) authenticationManager.get("email");
        // implement your own logic
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PaySlipDTO latestPaySlip = paySlipService.getCurrentPeriodPaySlipForEmployee(1l,loginEmployee.getId());
        System.out.println("Latest Pay Slip: " + latestPaySlip);
        System.out.println("Logged in employee: " + loginEmployee);
        model.addAttribute("employee", employee);
        model.addAttribute("latestPaySlip", latestPaySlip);
        model.addAttribute("title", "My Profile");
        model.addAttribute("subTitle", "View and update your information");
        return "mobile/profile";
    }

    @GetMapping("/employee/promotions")
    public String getMobilePromotions(Model model) {
        model.addAttribute("title", "Promotions");
        model.addAttribute("subTitle", "View promotion history");
        return "mobile/promotions";
    }

    @GetMapping("/employee/payroll")
    public String getMobilePayroll(Model model) {

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
        return "mobile/payroll";
    }

    @GetMapping("/employee/leave")
    public String getMobileLeave(Model model) {
        model.addAttribute("title", "Leave");
        model.addAttribute("subTitle", "Manage your time off");
        return "mobile/leave";
    }

    @GetMapping("/employee/performance")
    public String getMobilePerformance(Model model) {
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

        model.addAttribute("title", "Performance");
        model.addAttribute("subTitle", "View your KPI metrics");
        return "mobile/kpi";
    }

    @GetMapping("/employee/documents")
    public String getMobileDocuments(Model model) {
        model.addAttribute("title", "Documents");
        model.addAttribute("subTitle", "Manage your documents");
        return "mobile/documents";
    }
}
