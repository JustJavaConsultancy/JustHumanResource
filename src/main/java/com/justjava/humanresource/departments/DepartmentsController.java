package com.justjava.humanresource.departments;

import com.justjava.humanresource.hr.dto.DepartmentSummaryDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.DepartmentService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class DepartmentsController {
    @Autowired
    private SetupService setupService;

    @Autowired
    EmployeeOnboardingService employeeOnboardingService;

    @Autowired
    private DepartmentService departmentService;
    @GetMapping("/departments")
    public String getDepartments(Model model) {
        List<DepartmentSummaryDTO> departments = departmentService.getDepartmentSummaries();
        departments.forEach(
                dept -> System.out.println("Department: " + dept.getDepartmentId())
        );
        List<Employee> employees = employeeOnboardingService.getAllOnboardings();
        model.addAttribute("employees", employees.size());
        model.addAttribute("departments", departments);
        model.addAttribute("totalDepartments", departments.size());
        model.addAttribute("title","Department Management");
        model.addAttribute("subTitle","Organize and manage company departments, teams, and hierarchies");
        return "departments/main";
    }
    @PostMapping("/addDepartment")
    public String addDepartment(@RequestParam String departmentName) {
        // Logic to add a new department
        System.out.println("Adding Department: " + departmentName);
        setupService.createDepartment(departmentName);
        System.out.println("Department added successfully: " + departmentName);
        return "redirect:/departments";
    }
}
