package com.justjava.humanresource.departments;

import com.justjava.humanresource.hr.service.SetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DepartmentsController {
    @Autowired
    private SetupService setupService;

    @GetMapping("/departments")
    public String getDepartments(Model model) {
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
