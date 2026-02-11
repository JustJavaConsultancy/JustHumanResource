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
}
