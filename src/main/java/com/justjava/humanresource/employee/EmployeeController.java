package com.justjava.humanresource.employee;

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

import java.time.LocalDate;
import java.util.List;

@Controller
public class EmployeeController {
    @Autowired
    private SetupService setupService;

    @Autowired
    private EmployeeOnboardingService employeeOnboardingService;

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private PayrollSetupService payrollSetupService;

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
    public String getEmployeeProfile(Model model) { model.addAttribute("title", "Employee Profile"); model.addAttribute("subTitle", "View and update your personal information, job details, and performance data");
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
        model.addAttribute("title", "Performance Management");
        model.addAttribute("subTitle", "View your performance reviews, set goals, and track your progress");
        return "employees/kpi";
    }
    @GetMapping("employee/documents")
    public String getDocuments(Model model){
        model.addAttribute("title", "Document Management");
        model.addAttribute("subTitle", "View and manage your important documents such as contracts, certifications, and performance reviews");
        return "employees/documents";
    }
}
