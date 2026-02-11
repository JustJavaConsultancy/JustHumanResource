package com.justjava.humanresource.departments;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DepartmentsController {
    @GetMapping("/departments")
    public String getDepartments(Model model) {
        model.addAttribute("title","Department Management");
        model.addAttribute("subTitle","Organize and manage company departments, teams, and hierarchies");
        return "departments/main";
    }
}
