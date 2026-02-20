package com.justjava.humanresource.mobile;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.List;

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

    @GetMapping("/employees")
    public String getMobileEmployees(Model model) {
        List<Department> departments = setupService.getAllDepartments();
        List<PayGroup> payGroups = payrollSetupService.getAllPayGroups();
        List<JobGradeResponseDTO> jobGrades = setupService.getAllJobGrades();
        List<Employee> employees = employeeOnboardingService.getAllOnboardings();

        model.addAttribute("employees", employees);
        model.addAttribute("jobGrades", jobGrades);
        model.addAttribute("departments", departments);
        model.addAttribute("payGroups", payGroups);
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
        model.addAttribute("title", "Dashboard");
        model.addAttribute("subTitle", "Your personal overview");
        model.addAttribute("userName", "Jane Okafor");
        return "mobile/dashboard";
    }

    @GetMapping("/employee/profile")
    public String getMobileEmployeeProfile(Model model) {
        model.addAttribute("title", "My Profile");
        model.addAttribute("subTitle", "View and update your information");
        model.addAttribute("userName", "Jane Okafor");
        return "mobile/profile";
    }

    @GetMapping("/employee/promotions")
    public String getMobilePromotions(Model model) {
        model.addAttribute("title", "Promotions");
        model.addAttribute("subTitle", "View promotion history");
        model.addAttribute("userName", "Jane Okafor");
        return "mobile/promotions";
    }

    @GetMapping("/employee/payroll")
    public String getMobilePayroll(Model model) {
        model.addAttribute("title", "Payroll");
        model.addAttribute("subTitle", "View pay information");
        model.addAttribute("userName", "Jane Okafor");
        return "mobile/payroll";
    }

    @GetMapping("/employee/leave")
    public String getMobileLeave(Model model) {
        model.addAttribute("title", "Leave");
        model.addAttribute("subTitle", "Manage your time off");
        model.addAttribute("userName", "Jane Okafor");
        return "mobile/leave";
    }

    @GetMapping("/employee/performance")
    public String getMobilePerformance(Model model) {
        model.addAttribute("title", "Performance");
        model.addAttribute("subTitle", "View your KPI metrics");
        model.addAttribute("userName", "Jane Okafor");
        return "mobile/kpi";
    }

    @GetMapping("/employee/documents")
    public String getMobileDocuments(Model model) {
        model.addAttribute("title", "Documents");
        model.addAttribute("subTitle", "Manage your documents");
        model.addAttribute("userName", "Jane Okafor");
        return "mobile/documents";
    }
}
