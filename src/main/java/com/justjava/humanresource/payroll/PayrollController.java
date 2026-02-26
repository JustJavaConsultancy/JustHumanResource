package com.justjava.humanresource.payroll;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.PayFrequency;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.service.PayGroupService;
import com.justjava.humanresource.payroll.service.PaySlipService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.service.impl.PaySlipServiceImpl;
import com.justjava.humanresource.payroll.service.impl.PayrollPeriodServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Controller
public class PayrollController {
    @Autowired
    PayGroupService payGroupService;

    @Autowired
    private PayrollPeriodServiceImpl payrollPeriodService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private PayrollSetupService payrollSetupService;

    @Autowired
    private PaySlipService paySlipService;

    @Autowired
    private AuthenticationManager authenticationManager;



    @Autowired
    private EmployeeService employeeService;
    @GetMapping("/payroll")
    public String getPayroll(Model model) {
        List<Allowance> allowances = payrollSetupService.getActiveAllowances();
        List<Deduction> deductions = payrollSetupService.getActiveDeductions();
        List<PayGroup> payGroups = payrollSetupService.getAllPayGroups();
        List<Allowance> taxableAllowances = allowances
                .stream()
                .filter(Allowance:: isTaxable)
                .toList();
        List<Deduction> saturatoryDeductions = deductions
                .stream()
                .filter(Deduction:: isStatutory)
                .toList();
        System.out.println("The payroll is " + payrollPeriodService.getPeriodStatusForDate(1L,LocalDate.now()));
        model.addAttribute("payrollStatus", payrollPeriodService.getPeriodStatusForDate(1L,LocalDate.now()));
        model.addAttribute("allowances", allowances.size());
        model.addAttribute("taxableAllowances", taxableAllowances.size());
        model.addAttribute("saturatoryDeductions", saturatoryDeductions.size());
        model.addAttribute("deductions", deductions.size());
        model.addAttribute("payGroups", payGroups.size());
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
            @RequestParam(required = false) List<String> deductionSending) {
        if (deductionSending == null || deductionSending.isEmpty()) {
            return "redirect:/payroll/pay-group";
        }
        List<DeductionAttachmentRequest> requests = deductionSending.stream()
                .map(value -> {

                    System.out.println("RAW VALUE: " + value);

                    String[] parts = value.split(" ");

                    if (parts.length < 2) {
                        throw new IllegalArgumentException("Invalid allowance format: " + parts);
                    }

                    Long id = Long.parseLong(parts[0]);
                    BigDecimal amount = new BigDecimal(parts[1]);

                    DeductionAttachmentRequest req = new DeductionAttachmentRequest();
                    req.setDeductionId(id);
                    req.setOverridden(true);
                    req.setOverrideAmount(amount);
                    req.setEffectiveFrom(LocalDate.now());

                    return req;
                })
                .toList();

        payrollSetupService.addDeductionsToPayGroup(
                payGroupId,
                requests
        );

        return "redirect:/payroll/pay-group";
    }

    @GetMapping("/payroll/employee-payroll")
    public String getEmployeePayroll(Model model) {
        List<PaySlipDTO> paySlips = paySlipService.getPaySlipsForPeriod(YearMonth.now());
        paySlips.forEach(
                paySlip -> System.out.println(" Pay Slip for Employee ==="+paySlip.getEmployeeName()
                        +" the Pay Period ==="+paySlip.getPayDate()
                        +" the Gross Pay ==="+paySlip.getGrossPay()
                        +" the Total Deductions ==="+paySlip.getTotalDeductions()
                        +" the Net Pay ==="+paySlip.getNetPay())
        );
        model.addAttribute("paySlips", paySlips);
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
    @PostMapping("/setup/payroll/open")
    public String openPayroll() {
/*        String loginUserEmail = authenticationManager.get("email").toString();
        Employee loginEmployee = employeeService.getByEmail(loginUserEmail);*/
        YearMonth yearMonth = YearMonth.now();
        payrollPeriodService
                .openInitialPeriod(1L,
                        yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return "redirect:/payroll";
    }
    @PostMapping("/setup/payroll/close")
    public String closePayroll() {
        payrollPeriodService.closeAndOpenNext(1L);
        return "redirect:/payroll";
    }
}
