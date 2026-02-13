package com.justjava.humanresource.payroll;

import com.justjava.humanresource.core.enums.PayFrequency;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.service.SetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PayrollController {
    @Autowired
    private SetupService setupService;

    @GetMapping("/payroll")
    public String getPayroll(Model model) {
        model.addAttribute("title","Payroll Management");
        model.addAttribute("subTitle","Manage employee payroll, salary details, and payment history");
        return "payroll/main";
    }
    @PostMapping("/createPayGroup")
    public String createPayGroup(CreatePayGroupCommand command) {
        setupService.createPayGroup(command);
        return "redirect:/payroll";
    }

}
