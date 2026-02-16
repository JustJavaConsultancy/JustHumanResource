package com.justjava.humanresource.payroll;

import com.justjava.humanresource.core.enums.PayFrequency;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.service.PayGroupService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
public class PayrollController {
    @Autowired
    PayGroupService payGroupService;

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
    @GetMapping("/payroll/items")
    public String getPayrollItems(Model model) {
        List<Allowance> allowances = payrollSetupService.getActiveAllowances();
        List<Deduction> deductions = payrollSetupService.getActiveDeductions();


        model.addAttribute("allowances", allowances);
        model.addAttribute("deductions", deductions);
        model.addAttribute("title","Payroll Management");
        model.addAttribute("subTitle","Manage employee payroll, salary details, and payment history");
        return "payroll/fragments/payroll-items";
    }
    @GetMapping("/payroll/pay-group")
    public String getPayGroup(Model model){
        List<Deduction> deductions = payrollSetupService.getActiveDeductions();
        List<Allowance> allowances = payrollSetupService.getActiveAllowances();
        List<PayGroup> payGroups = payrollSetupService.getAllPayGroups();

        LocalDate currentDate = LocalDate.now();

        List<PayGroupFullViewDTO> payGroupFullList = payGroups.stream()
                .map(payGroup -> PayGroupFullViewDTO.builder()
                        .payGroup(payGroup)
                        .allowances(payGroupService.getAllowances(payGroup.getId(), currentDate))
                        .deductions(payGroupService.getDeductions(payGroup.getId(), currentDate))
                        .employees(payGroupService.getEmployees(payGroup.getId(), currentDate))
                        .build()
                )
                .toList();
        payGroupFullList.forEach(
                payGroupFullViewDTO -> System.out.println(" The Pay Group Name ==="+payGroupFullViewDTO.getPayGroup().getName()
                        +" the Allowances ==="+payGroupFullViewDTO.getAllowances().size()
                        +" the Deductions ==="+payGroupFullViewDTO.getDeductions().size()
                        +" the Employees ==="+payGroupFullViewDTO.getEmployees().size())
        );

        model.addAttribute("payGroups", payGroupFullList);
        model.addAttribute("allowances", allowances);
        model.addAttribute("deductions", deductions);
        model.addAttribute("title","Payroll Management");
        model.addAttribute("subTitle","Manage employee payroll, salary details, and payment history");

        return "payroll/fragments/paygroups";
    }
    @PostMapping("/setup/paygroup/allowances")
    public String attachAllowancesToPayGroup(
            @RequestParam Long payGroupId,
            @RequestParam(required = false) List<String> allowancesSending) {

        if (allowancesSending == null || allowancesSending.isEmpty()) {
            return "redirect:/payroll/pay-group";
        }

        List<AllowanceAttachmentRequest> requests = allowancesSending.stream()
                .map(value -> {

                    System.out.println("RAW VALUE: " + value);

                    String[] parts = value.split(" ");

                    if (parts.length < 2) {
                        throw new IllegalArgumentException("Invalid allowance format: " + parts);
                    }

                    Long id = Long.parseLong(parts[0]);
                    BigDecimal amount = new BigDecimal(parts[1]);

                    AllowanceAttachmentRequest req = new AllowanceAttachmentRequest();
                    req.setAllowanceId(id);
                    req.setOverridden(true);
                    req.setOverrideAmount(amount);
                    req.setEffectiveFrom(LocalDate.now());

                    return req;
                })
                .toList();


        payrollSetupService.addAllowancesToPayGroup(payGroupId, requests);

        return "redirect:/payroll/pay-group";
    }



    @PostMapping("/setup/paygroup/deductions")
    public String attachDeductionsToPayGroup(
            @RequestParam Long payGroupId,
            List<DeductionAttachmentRequest> requests) {
        System.out.println(" The Pay Group ID ==="+payGroupId);
        payrollSetupService.addDeductionsToPayGroup(
                payGroupId,
                requests
        );

        return "redirect:/payroll/pay-group";
    }

    @GetMapping("/payroll/employee-payroll")
    public String getEmployeePayroll(Model model) {
        model.addAttribute("title", "Payroll Management");
        model.addAttribute("subTitle", "Manage employee payroll, salary details, and payment history");
        return "payroll/fragments/employee-payroll";
    }
    @PostMapping("/createPayGroup")
    public String createPayGroup(CreatePayGroupCommand command) {
        setupService.createPayGroup(command);
        return "redirect:/payroll/pay-group";
    }
    @PostMapping("/setup/allowance")
    public String createAllowance(Allowance allowance) {
        payrollSetupService.createAllowance(allowance);
        return "redirect:/payroll/items";
    }
    @PostMapping("/setup/deduction")
    public String createDeduction(Deduction deduction) {
        payrollSetupService.createDeduction(deduction);
        return "redirect:/payroll/items";
    }
}
