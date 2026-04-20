package com.justjava.humanresource.payroll;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.service.PayGroupService;
import com.justjava.humanresource.payroll.service.PaySlipService;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.dto.EmployeeGroupedReportDTO;
import com.justjava.humanresource.payroll.enums.EmployeeGroupBy;
import com.justjava.humanresource.payroll.report.services.ReportingService;
import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.dto.FutureEmployeeAllowanceDTO;
import java.util.Comparator;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class PayrollController {
    @Autowired
    PayGroupService payGroupService;

    @Autowired
    private PayrollPeriodService payrollPeriodService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private PayrollSetupService payrollSetupService;

    @Autowired
    private PaySlipService paySlipService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private EmployeeOnboardingService employeeOnboardingService;

    @Autowired
    private ReportingService reportingService;

    @Autowired
    private EmployeeService employeeService;

    @GetMapping("/payroll")
    public String getPayroll(Model model) {
        List<Employee> employees = employeeOnboardingService.getAllOnboardings();
        List<Allowance> allowances = payrollSetupService.getActiveAllowances();
        List<Deduction> deductions = payrollSetupService.getActiveDeductions();
        List<TaxRelief> taxReliefs = payrollSetupService.getActiveTaxReliefs();
        List<PayGroup> payGroups = payrollSetupService.getAllPayGroups();
        List<Allowance> taxableAllowances = allowances
                .stream()
                .filter(Allowance::isTaxable)
                .toList();
        List<Deduction> saturatoryDeductions = deductions
                .stream()
                .filter(Deduction::isStatutory)
                .toList();

        model.addAttribute("payrollStatus", payrollPeriodService.getPeriodStatusForDate(1L, LocalDate.now()));
        model.addAttribute("allowances", allowances.size());
        model.addAttribute("employees", employees.size());
        model.addAttribute("taxableAllowances", taxableAllowances.size());
        model.addAttribute("saturatoryDeductions", saturatoryDeductions.size());
        model.addAttribute("deductions", deductions.size());
        model.addAttribute("taxReliefs", taxReliefs.size());
        model.addAttribute("payGroups", payGroups.size());
        model.addAttribute("title", "Payroll Management");
        model.addAttribute("subTitle", "Manage employee payroll, salary details, and payment history");
        return "payroll/main";
    }

    @GetMapping("/payroll/items")
    public String getPayrollItems(Model model) {
        List<Allowance> allowances = payrollSetupService.getActiveAllowances();
        List<Deduction> deductions = payrollSetupService.getActiveDeductions();
        List<TaxRelief> taxReliefs = payrollSetupService.getActiveTaxReliefs();

        model.addAttribute("allowances", allowances);
        model.addAttribute("deductions", deductions);
        model.addAttribute("taxReliefs", taxReliefs);
        model.addAttribute("title", "Payroll Management");
        model.addAttribute("subTitle", "Manage employee payroll, salary details, and payment history");
        return "payroll/fragments/payroll-items";
    }

    @GetMapping("/payroll/pay-group")
    public String getPayGroup(Model model) {
        List<Deduction> deductions = payrollSetupService.getActiveDeductions();
        List<Allowance> allowances = payrollSetupService.getActiveAllowances();
        List<TaxRelief> taxReliefs = payrollSetupService.getActiveTaxReliefs();
        List<PayGroup> payGroups = payrollSetupService.getAllPayGroups();

        LocalDate currentDate = LocalDate.now();

        List<PayGroupFullViewDTO> payGroupFullList = payGroups.stream()
                .map(payGroup -> PayGroupFullViewDTO.builder()
                        .payGroup(payGroup)
                        .allowances(payGroupService.getAllAssignedAllowances(payGroup.getId(), currentDate))
                        .deductions(payGroupService.getAllAssignedDeductions(payGroup.getId(), currentDate))
                        .taxReliefs(payGroupService.getAllAssignedTaxReliefs(payGroup.getId(), currentDate))
                        .employees(payGroupService.getEmployees(payGroup.getId(), currentDate))
                        .build())
                .toList();

        model.addAttribute("taxReliefs", taxReliefs);
        model.addAttribute("payGroups", payGroupFullList);
        model.addAttribute("allowances", allowances);
        model.addAttribute("deductions", deductions);
        model.addAttribute("title", "Payroll Management");
        model.addAttribute("subTitle", "Manage employee payroll, salary details, and payment history");

        return "payroll/fragments/paygroups";
    }

    /**
     * API endpoint to get currently assigned pay items for a paygroup
     */
    @GetMapping("/api/paygroup/{payGroupId}/pay-items")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPayGroupPayItems(@PathVariable Long payGroupId) {
        LocalDate currentDate = LocalDate.now();

        List<PayGroupAllowanceViewDTO> allowances = payGroupService.getAllAssignedAllowances(payGroupId, currentDate);
        List<PayGroupDeductionViewDTO> deductions = payGroupService.getAllAssignedDeductions(payGroupId, currentDate);
        List<PayGroupTaxReliefViewDTO> taxReliefs = payGroupService.getAllAssignedTaxReliefs(payGroupId, currentDate);

        return ResponseEntity.ok(Map.of(
                "allowances", allowances.stream().map(PayGroupAllowanceViewDTO::getAllowanceId).toList(),
                "deductions", deductions.stream().map(PayGroupDeductionViewDTO::getDeductionId).toList(),
                "taxReliefs", taxReliefs.stream().map(PayGroupTaxReliefViewDTO::getTaxReliefId).toList()
        ));
    }

    @PostMapping("/setup/paygroup/allowances")
    public String attachAllowancesToPayGroup(
            @RequestParam Long payGroupId,
            @RequestParam(required = false) List<Long> allowanceIds,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        if (allowanceIds == null || allowanceIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("info", "No allowances selected");
            return "redirect:/payroll/pay-group";
        }

        // Get all allowances to lookup amounts
        Map<Long, Allowance> allowanceMap = payrollSetupService.getActiveAllowances()
                .stream()
                .collect(Collectors.toMap(Allowance::getId, a -> a));

        List<AllowanceAttachmentRequest> requests = allowanceIds.stream()
                .map(id -> {
                    Allowance allowance = allowanceMap.get(id);
                    if (allowance == null) return null;

                    AllowanceAttachmentRequest req = new AllowanceAttachmentRequest();
                    req.setAllowanceId(id);
                    req.setOverridden(false);
                    req.setOverrideAmount(allowance.getAmount());

                    // Check for start date parameter
                    String startDateStr = allParams.get("startDate_" + id);
                    if (startDateStr != null && !startDateStr.isEmpty()) {
                        req.setEffectiveFrom(LocalDate.parse(startDateStr));
                    } else {
                        req.setEffectiveFrom(LocalDate.now());
                    }

                    return req;
                })
                .filter(Objects::nonNull)
                .toList();

        if (!requests.isEmpty()) {
            payrollSetupService.addAllowancesToPayGroup(payGroupId, requests);
            redirectAttributes.addFlashAttribute("success", "Allowances updated successfully");
        }

        return "redirect:/payroll/pay-group";
    }

    @PostMapping("/setup/paygroup/deductions")
    public String attachDeductionsToPayGroup(
            @RequestParam Long payGroupId,
            @RequestParam(required = false) List<Long> deductionIds,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        if (deductionIds == null || deductionIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("info", "No deductions selected");
            return "redirect:/payroll/pay-group";
        }

        // Get all deductions to lookup amounts
        Map<Long, Deduction> deductionMap = payrollSetupService.getActiveDeductions()
                .stream()
                .collect(Collectors.toMap(Deduction::getId, d -> d));

        List<DeductionAttachmentRequest> requests = deductionIds.stream()
                .map(id -> {
                    Deduction deduction = deductionMap.get(id);
                    if (deduction == null) return null;

                    DeductionAttachmentRequest req = new DeductionAttachmentRequest();
                    req.setDeductionId(id);
                    req.setOverridden(false);
                    req.setOverrideAmount(deduction.getAmount());

                    // Check for start date parameter
                    String startDateStr = allParams.get("startDate_" + id);
                    if (startDateStr != null && !startDateStr.isEmpty()) {
                        req.setEffectiveFrom(LocalDate.parse(startDateStr));
                    } else {
                        req.setEffectiveFrom(LocalDate.now());
                    }

                    return req;
                })
                .filter(Objects::nonNull)
                .toList();

        if (!requests.isEmpty()) {
            payrollSetupService.addDeductionsToPayGroup(payGroupId, requests);
            redirectAttributes.addFlashAttribute("success", "Deductions updated successfully");
        }

        return "redirect:/payroll/pay-group";
    }

    @PostMapping("/setup/paygroup/taxreliefs")
    public String attachTaxReliefsToPayGroup(
            @RequestParam Long payGroupId,
            @RequestParam(required = false) List<Long> taxReliefIds,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        if (taxReliefIds == null || taxReliefIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("info", "No tax reliefs selected");
            return "redirect:/payroll/pay-group";
        }

        // Get all tax reliefs to lookup amounts
        Map<Long, TaxRelief> taxReliefMap = payrollSetupService.getActiveTaxReliefs()
                .stream()
                .collect(Collectors.toMap(TaxRelief::getId, t -> t));

        List<TaxReliefAttachmentRequest> requests = taxReliefIds.stream()
                .map(id -> {
                    TaxRelief taxRelief = taxReliefMap.get(id);
                    if (taxRelief == null) return null;

                    TaxReliefAttachmentRequest req = new TaxReliefAttachmentRequest();
                    req.setTaxReliefId(id);
                    req.setOverridden(false);
                    req.setOverrideAmount(taxRelief.getAmount());

                    // Check for start date parameter
                    String startDateStr = allParams.get("startDate_" + id);
                    if (startDateStr != null && !startDateStr.isEmpty()) {
                        req.setEffectiveFrom(LocalDate.parse(startDateStr));
                    } else {
                        req.setEffectiveFrom(LocalDate.now());
                    }

                    return req;
                })
                .filter(Objects::nonNull)
                .toList();

        if (!requests.isEmpty()) {
            payrollSetupService.addTaxReliefsToPayGroup(payGroupId, requests);
            redirectAttributes.addFlashAttribute("success", "Tax reliefs updated successfully");
        }

        return "redirect:/payroll/pay-group";
    }



    @GetMapping("/payroll/employee-payroll")
    public String getEmployeePayroll(Model model) {
        // Fetch and sort current payroll runs by Employee ID
        List<PayrollRun> payrollRuns = paySlipService.getCurrentPeriodPayrollRuns(1L).stream()
                .sorted((a, b) -> a.getEmployee().getId().compareTo(b.getEmployee().getId()))
                .toList();

        // Fetch and sort previous payslips by Employee ID
        List<PaySlipDTO> previousPaySlips = paySlipService.getAllClosedPeriodPaySlips(1L).stream()
                .sorted((a, b) -> a.getEmployeeId().compareTo(b.getEmployeeId()))
                .toList();

        // Auto-generate payslips for any POSTED run that doesn't have one yet
        for (PayrollRun run : payrollRuns) {
            if (run.getStatus() == PayrollRunStatus.POSTED &&
                    !paySlipService.existsForPayrollRun(run.getId())) {
                try {
                    paySlipService.generatePaySlip(run.getId());
                } catch (Exception ignored) {}
            }
        }

        List<PaySlipDTO> currentPaySlips = List.of();
        try {
            // Fetch and sort current period payslips
            currentPaySlips = paySlipService.getCurrentPeriodPaySlips(1L).stream()
                    .sorted((a, b) -> a.getEmployeeId().compareTo(b.getEmployeeId()))
                    .toList();
        } catch (Exception e) {  }

        List<FutureEmployeeAllowanceDTO> futureAllowances = List.of();
        try {
            futureAllowances = payrollRuns.stream()
                    .flatMap(run -> payrollSetupService
                            .getFutureAllowancesForEmployee(run.getEmployee().getId())
                            .stream())
                    .distinct()
                    .toList();
        } catch (Exception ignored) {}

        model.addAttribute("payrollRuns", payrollRuns);
        model.addAttribute("currentPaySlips", currentPaySlips);
        model.addAttribute("previousPeriods", previousPaySlips);
        model.addAttribute("futureAllowances", futureAllowances);
        model.addAttribute("title", "Payroll Management");
        model.addAttribute("subTitle", "Manage employee payroll, salary details, and payment history");

        return "payroll/fragments/employee-payroll";
    }


    @PostMapping("/createPayGroup")
    public String createPayGroup(CreatePayGroupCommand command, RedirectAttributes redirectAttributes) {
        setupService.createPayGroup(command);
        redirectAttributes.addFlashAttribute("success", "Pay group created successfully");
        return "redirect:/payroll/pay-group";
    }

    @PostMapping("/setup/allowance")
    public String createAllowance(Allowance allowance, RedirectAttributes redirectAttributes) {
        if (allowance.getAmount() == null)
            allowance.setAmount(BigDecimal.ZERO);
        payrollSetupService.createAllowance(allowance);
        redirectAttributes.addFlashAttribute("success", "Allowance created successfully");
        return "redirect:/payroll/items";
    }

    @PostMapping("/setup/deduction")
    public String createDeduction(Deduction deduction, RedirectAttributes redirectAttributes) {
        if (deduction.getAmount() == null)
            deduction.setAmount(BigDecimal.ZERO);
        payrollSetupService.createDeduction(deduction);
        redirectAttributes.addFlashAttribute("success", "Deduction created successfully");
        return "redirect:/payroll/items";
    }

    @PostMapping("/setup/taxrelief")
    public String createTaxRelief(TaxRelief taxRelief, RedirectAttributes redirectAttributes) {
        if (taxRelief.getAmount() == null)
            taxRelief.setAmount(BigDecimal.ZERO);
        payrollSetupService.createTaxRelief(taxRelief);
        redirectAttributes.addFlashAttribute("success", "Tax relief created successfully");
        return "redirect:/payroll/items";
    }

    @PostMapping("/setup/payroll/open")
    public String openPayroll(RedirectAttributes redirectAttributes) {
        YearMonth yearMonth = YearMonth.now();
        payrollPeriodService.openInitialPeriod(1L, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        redirectAttributes.addFlashAttribute("success", "Payroll period opened");
        return "redirect:/payroll";
    }

    @PostMapping("/setup/payroll/close")
    public String closePayroll(RedirectAttributes redirectAttributes) {
        try {
            payrollPeriodService.initiatePeriodCloseApproval(1L);
            redirectAttributes.addFlashAttribute("success", "Payroll locked!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/payroll/employee-payroll";
    }


    @GetMapping("/payroll/grouped-report")
    public String getReport(
            @RequestParam(defaultValue = "GRADE") String groupBy,
            Model model) {

        YearMonth currentMonth = YearMonth.now();

        List<EmployeeGroupedReportDTO> report = reportingService.getGroupedReport(
                1L,
                currentMonth.atDay(1),
                currentMonth.atEndOfMonth(),
                EmployeeGroupBy.valueOf(groupBy)
        );

        Map<String, Object> grandTotals = reportingService.calculateGrandTotals(report);

        model.addAttribute("report", report);
        model.addAttribute("totals", grandTotals);
        model.addAttribute("groupBy", groupBy);
        model.addAttribute("title", "Payroll Management");
        model.addAttribute("subTitle", "Manage employee payroll, salary details, and payment history");
        return "payroll/fragments/employee-payroll";
    }




}