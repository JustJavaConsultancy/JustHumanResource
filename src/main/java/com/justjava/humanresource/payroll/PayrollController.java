package com.justjava.humanresource.payroll;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PayrollController {
    @GetMapping("/payroll")
    public String getPayroll(Model model) {
        model.addAttribute("title","Payroll Management");
        model.addAttribute("subTitle","Manage employee payroll, salary details, and payment history");
        return "payroll/main";
    }

}
