package com.justjava.humanresource.employee;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EmployeeController {
    @GetMapping("/employees")
    public String getEmployees(Model model) {
        model.addAttribute("title","Employee Management");
        model.addAttribute("subTitle","Manage employee records, payroll, and performance data");
        return "employees/main";
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
