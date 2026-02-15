package com.justjava.humanresource.payroll;

import com.justjava.humanresource.core.enums.PayFrequency;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class PayrollController {
    @Autowired
    private SetupService setupService;

    @Autowired
    private PayrollSetupService payrollSetupService;

    @GetMapping("/payroll")
    public String getPayroll(Model model) {
        List<Allowance> allowances = payrollSetupService.getActiveAllowances();
        List<Deduction> deductions = payrollSetupService.getActiveDeductions();
        List<Allowance> taxableAllowances = allowances
                .stream()
                .filter(Allowance:: isTaxable)
                .toList();
        List<Deduction> saturatoryDeductions = deductions
                .stream()
                .filter(Deduction:: isStatutory)
                .toList();
        System.out.println("Allowance amount is " + allowances.size() + "Deduction amount is " + deductions.size());
        model.addAttribute("allowances", allowances);
        model.addAttribute("taxableAllowances", taxableAllowances.size());
        model.addAttribute("saturatoryDeductions", saturatoryDeductions.size());
        model.addAttribute("deductions", deductions);
        model.addAttribute("title","Payroll Management");
        model.addAttribute("subTitle","Manage employee payroll, salary details, and payment history");
        return "payroll/main";
    }
    @PostMapping("/createPayGroup")
    public String createPayGroup(CreatePayGroupCommand command) {
        setupService.createPayGroup(command);
        return "redirect:/payroll";
    }
    @PostMapping("/setup/allowance")
    public String createAllowance(Allowance allowance) {
        payrollSetupService.createAllowance(allowance);
        return "redirect:/payroll";
    }
    @PostMapping("/setup/deduction")
    public String createDeduction(Deduction deduction) {
        payrollSetupService.createDeduction(deduction);
        return "redirect:/payroll";
    }
}
