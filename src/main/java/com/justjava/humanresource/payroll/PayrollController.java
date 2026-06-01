package com.justjava.humanresource.payroll;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.JobHrEmployeeAccessService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.service.PayGroupService;
import com.justjava.humanresource.payroll.service.PaySlipService;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.dto.EmployeeGroupedReportDTO;
import com.justjava.humanresource.payroll.dto.AllowanceGroupReportDTO;
import com.justjava.humanresource.payroll.dto.AllowanceReportLineDTO;
import com.justjava.humanresource.payroll.enums.EmployeeGroupBy;
import com.justjava.humanresource.payroll.report.services.ReportingService;
import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.dto.FutureEmployeeAllowanceDTO;
import com.justjava.humanresource.payroll.dto.PayePensionGroupDTO;
import com.justjava.humanresource.payroll.dto.PayePensionLineDTO;

import java.math.RoundingMode;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;


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
import java.util.Set;

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

    @Autowired
    FlowableTaskService flowableTaskService;

    @Autowired
    JobHrEmployeeAccessService jobHrEmployeeAccessService;



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

        // Soft-delete allowances that were unchecked (not in the submitted list)
        List<Long> submittedIds = allowanceIds != null ? allowanceIds : List.of();
        payrollSetupService.deactivateRemovedAllowancesFromPayGroup(payGroupId, submittedIds);

        if (submittedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("info", "All allowances removed from pay group");
            return "redirect:/payroll/pay-group";
        }

        // Get all allowances to lookup amounts
        Map<Long, Allowance> allowanceMap = payrollSetupService.getActiveAllowances()
                .stream()
                .collect(Collectors.toMap(Allowance::getId, a -> a));

        List<AllowanceAttachmentRequest> requests = submittedIds.stream()
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

        // Soft-delete deductions that were unchecked (not in the submitted list)
        List<Long> submittedIds = deductionIds != null ? deductionIds : List.of();
        payrollSetupService.deactivateRemovedDeductionsFromPayGroup(payGroupId, submittedIds);

        if (submittedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("info", "All deductions removed from pay group");
            return "redirect:/payroll/pay-group";
        }

        // Get all deductions to lookup amounts
        Map<Long, Deduction> deductionMap = payrollSetupService.getActiveDeductions()
                .stream()
                .collect(Collectors.toMap(Deduction::getId, d -> d));

        List<DeductionAttachmentRequest> requests = submittedIds.stream()
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

        // Soft-delete tax reliefs that were unchecked (not in the submitted list)
        List<Long> submittedIds = taxReliefIds != null ? taxReliefIds : List.of();
        payrollSetupService.deactivateRemovedTaxReliefsFromPayGroup(payGroupId, submittedIds);

        if (submittedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("info", "All tax reliefs removed from pay group");
            return "redirect:/payroll/pay-group";
        }

        // Get all tax reliefs to lookup amounts
        Map<Long, TaxRelief> taxReliefMap = payrollSetupService.getActiveTaxReliefs()
                .stream()
                .collect(Collectors.toMap(TaxRelief::getId, t -> t));

        List<TaxReliefAttachmentRequest> requests = submittedIds.stream()
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

        boolean isRestrictedHr = authenticationManager.isRestrictedHr();

        // Fetch and sort current payroll runs by Employee ID,
        // excluding restricted employees if caller is restrictedHr
        List<PayrollRun> payrollRuns = paySlipService.getCurrentPeriodPayrollRuns(1L).stream()
                .filter(r -> {
                    Employee emp = r.getEmployee();
                    if (isRestrictedHr && emp.isRestrictedVisibility()) return false;
                    if (jobHrEmployeeAccessService.isJobHrScopedUser()) {
                        Long actorGradeId = jobHrEmployeeAccessService.getLoggedInJobGradeId();
                        Long empGradeId = emp.getJobStep() != null && emp.getJobStep().getJobGrade() != null
                                ? emp.getJobStep().getJobGrade().getId() : null;
                        return java.util.Objects.equals(actorGradeId, empGradeId);
                    }
                    return true;
                })
                .sorted((a, b) -> a.getEmployee().getId().compareTo(b.getEmployee().getId()))
                .toList();

        // Build a set of visible employee IDs for fast DTO filtering
        boolean needsFiltering = isRestrictedHr || jobHrEmployeeAccessService.isJobHrScopedUser();
        Set<Long> visibleEmployeeIds = needsFiltering
                ? payrollRuns.stream()
                .map(r -> r.getEmployee().getId())
                .collect(Collectors.toSet())
                : null;

        // Fetch and sort previous payslips, excluding restricted employees
        List<PaySlipDTO> previousPaySlips = paySlipService.getAllClosedPeriodPaySlips(1L).stream()
                .filter(ps -> visibleEmployeeIds == null || visibleEmployeeIds.contains(ps.getEmployeeId()))
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
            currentPaySlips = paySlipService.getCurrentPeriodPaySlips(1L).stream()
                    .filter(ps -> visibleEmployeeIds == null || visibleEmployeeIds.contains(ps.getEmployeeId()))
                    .sorted((a, b) -> a.getEmployeeId().compareTo(b.getEmployeeId()))
                    .map(ps -> {
                        List<FutureEmployeeAllowanceDTO> futureAllowances = List.of();
                        try {
                            futureAllowances = payrollSetupService
                                    .getFutureAllowancesForEmployee(ps.getEmployeeId());
                        } catch (Exception ignored) {}
                        return PaySlipDTO.builder()
                                .id(ps.getId())
                                .employeeId(ps.getEmployeeId())
                                .employeeName(ps.getEmployeeName())
                                .payrollRunId(ps.getPayrollRunId())
                                .payDate(ps.getPayDate())
                                .versionNumber(ps.getVersionNumber())
                                .basicSalary(ps.getBasicSalary())
                                .grossPay(ps.getGrossPay())
                                .totalDeductions(ps.getTotalDeductions())
                                .netPay(ps.getNetPay())
                                .nonGrossEarnings(ps.getNonGrossEarnings())
                                .allowances(ps.getAllowances())
                                .deductions(ps.getDeductions())
                                .futureAllowances(futureAllowances)
                                .appliedTaxBandSummary(ps.getAppliedTaxBandSummary())
                                .appliedPensionSchemeName(ps.getAppliedPensionSchemeName())
                                .pensionAmount(ps.getPensionAmount())
                                .status(ps.getStatus())
                                .bankName(ps.getBankName())
                                .bankAccountNumber(ps.getBankAccountNumber())
                                .build();
                    })
                    .toList();
        } catch (Exception e) {}

        model.addAttribute("payrollRuns", payrollRuns);
        model.addAttribute("currentPaySlips", currentPaySlips);
        model.addAttribute("previousPeriods", previousPaySlips);
        model.addAttribute("title", "Payroll Management");
        model.addAttribute("subTitle", "Manage employee payroll, salary details, and payment history");

        List<FlowableTaskDTO> pendingLockTasks = flowableTaskService
                .getTasksByTaskDefinition("financeOfficer", "payrollPeriodCloseProcess");

        model.addAttribute("lockPending", !pendingLockTasks.isEmpty());
        model.addAttribute("isRestrictedHr", isRestrictedHr);                                    // ADD THIS
        model.addAttribute("isJobHr", jobHrEmployeeAccessService.isJobHrScopedUser());

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

        if (authenticationManager.isRestrictedHr()) {
            return "redirect:/payroll/employee-payroll";
        }

        YearMonth currentMonth = YearMonth.now();

        // Build scoped employee ID set for jobHR
        final Set<Long> scopedIds;
        if (jobHrEmployeeAccessService.isJobHrScopedUser()) {
            Long actorGradeId = jobHrEmployeeAccessService.getLoggedInJobGradeId();
            scopedIds = employeeOnboardingService.getAllOnboardings().stream()
                    .filter(e -> e.getJobStep() != null
                            && e.getJobStep().getJobGrade() != null
                            && Objects.equals(e.getJobStep().getJobGrade().getId(), actorGradeId))
                    .map(Employee::getId)
                    .collect(Collectors.toSet());
        } else {
            scopedIds = null;
        }

        List<EmployeeGroupedReportDTO> report = reportingService.getGroupedReport(
                        1L,
                        currentMonth.atDay(1),
                        currentMonth.atEndOfMonth(),
                        EmployeeGroupBy.valueOf(groupBy)
                ).stream()
                .map(group -> {
                    if (scopedIds != null) {
                        group.getEmployees().removeIf(e -> !scopedIds.contains(e.getEmployeeId()));
                    }
                    group.getEmployees().sort(Comparator.comparing(e -> e.getEmployeeId()));
                    return group;
                })
                .filter(group -> !group.getEmployees().isEmpty()) // remove empty groups
                .toList();

        Map<String, Object> grandTotals = reportingService.calculateGrandTotals(report);

        model.addAttribute("report", report);
        model.addAttribute("totals", grandTotals);
        model.addAttribute("groupBy", groupBy);
        model.addAttribute("title", "Payroll Management");
        model.addAttribute("subTitle", "Manage employee payroll, salary details, and payment history");
        return "payroll/fragments/employee-payroll";
    }

    @GetMapping("/payroll/payitems-report")
    public String getPayItemsReport(
            @RequestParam(defaultValue = "GRADE") String groupBy,
            Model model) {

        if (authenticationManager.isRestrictedHr()) {
            return "redirect:/payroll/employee-payroll";
        }

        YearMonth currentMonth = YearMonth.now();

        // ── 1. Build scoped employee ID set for jobHR (same pattern as grouped-report) ──
        final Set<Long> scopedIds;
        if (jobHrEmployeeAccessService.isJobHrScopedUser()) {
            Long actorGradeId = jobHrEmployeeAccessService.getLoggedInJobGradeId();
            scopedIds = employeeOnboardingService.getAllOnboardings().stream()
                    .filter(e -> e.getJobStep() != null
                            && e.getJobStep().getJobGrade() != null
                            && Objects.equals(e.getJobStep().getJobGrade().getId(), actorGradeId))
                    .map(Employee::getId)
                    .collect(Collectors.toSet());
        } else {
            scopedIds = null;
        }

        // ── 2. Load all current-period payslips ──
        List<PaySlipDTO> paySlips = paySlipService.getCurrentPeriodPaySlips(1L);

        // ── 3. Filter to scoped employees if jobHR ──
        if (scopedIds != null) {
            paySlips = paySlips.stream()
                    .filter(ps -> scopedIds.contains(ps.getEmployeeId()))
                    .collect(Collectors.toList());
        }

        // ── 4. Build employeeId → Employee map for group-name resolution ──
        Map<Long, Employee> employeeMap = employeeOnboardingService.getAllOnboardings().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));

        // ── 5. Group payslips by chosen dimension ──
        Map<String, List<PaySlipDTO>> grouped = new LinkedHashMap<>();
        for (PaySlipDTO ps : paySlips) {
            Employee emp = employeeMap.get(ps.getEmployeeId());
            String key = resolvePayItemsGroupName(emp, groupBy);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(ps);
        }

        // ── 6. Build report groups ──
        List<AllowanceGroupReportDTO> payitemsReport = new ArrayList<>();

        for (Map.Entry<String, List<PaySlipDTO>> entry : grouped.entrySet()) {
            AllowanceGroupReportDTO group = new AllowanceGroupReportDTO();
            group.setGroupName(entry.getKey());
            group.setEmployeeCount((long) entry.getValue().size());

            // Sum each allowance code across all employees in this group
            Map<String, AllowanceReportLineDTO> codeMap = new LinkedHashMap<>();
            for (PaySlipDTO slip : entry.getValue()) {
                if (slip.getAllowances() == null) continue;
                for (PaySlipLineDTO line : slip.getAllowances()) {
                    // Skip residual adjustment — not displayed anywhere (mirrors employee-payroll.html)
                    if ("RESIDUAL".equals(line.getCode()) || "Residual Adjustment".equals(line.getDescription())) continue;
                    codeMap.compute(line.getCode(), (code, existing) -> {
                        BigDecimal amt = line.getAmount() != null ? line.getAmount() : BigDecimal.ZERO;
                        if (existing == null) {
                            return new AllowanceReportLineDTO(
                                    line.getCode(),
                                    line.getDescription(),
                                    amt,
                                    line.isTaxable(),
                                    !line.isOutOfPayroll()
                            );
                        }
                        existing.setTotalAmount(existing.getTotalAmount().add(amt));
                        return existing;
                    });
                }
            }

            List<AllowanceReportLineDTO> lines = new ArrayList<>(codeMap.values());
            BigDecimal groupTotal = lines.stream()
                    .map(AllowanceReportLineDTO::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            group.setAllowances(lines);
            group.setGroupTotal(groupTotal);
            payitemsReport.add(group);
        }

        // Sort groups alphabetically
        payitemsReport.sort(Comparator.comparing(AllowanceGroupReportDTO::getGroupName));

        // ── 7. Compute grand totals across all groups ──
        Map<String, AllowanceReportLineDTO> grandCodeMap = new LinkedHashMap<>();
        for (AllowanceGroupReportDTO group : payitemsReport) {
            for (AllowanceReportLineDTO line : group.getAllowances()) {
                grandCodeMap.compute(line.getCode(), (code, existing) -> {
                    if (existing == null) {
                        return new AllowanceReportLineDTO(
                                line.getCode(), line.getDescription(),
                                line.getTotalAmount(), line.isTaxable(), line.isPartOfGross()
                        );
                    }
                    existing.setTotalAmount(existing.getTotalAmount().add(line.getTotalAmount()));
                    return existing;
                });
            }
        }

        BigDecimal grandTotal = grandCodeMap.values().stream()
                .map(AllowanceReportLineDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("payitemsReport",  payitemsReport);
        model.addAttribute("grandAllowances", new ArrayList<>(grandCodeMap.values()));
        model.addAttribute("grandTotal",      grandTotal);
        model.addAttribute("totalGroups",     (long) payitemsReport.size());
        model.addAttribute("totalEmployees",  payitemsReport.stream().mapToLong(AllowanceGroupReportDTO::getEmployeeCount).sum());
        model.addAttribute("groupBy",         groupBy);
        model.addAttribute("reportMonth",     currentMonth.toString());
        model.addAttribute("title",           "Payroll Management");
        model.addAttribute("subTitle",        "Manage employee payroll, salary details, and payment history");
        return "payroll/fragments/payitems-report";
    }

    private String resolvePayItemsGroupName(Employee emp, String groupBy) {
        if (emp == null) return "Unknown";
        switch (groupBy.toUpperCase()) {
            case "GRADE":
                return (emp.getJobStep() != null && emp.getJobStep().getJobGrade() != null)
                        ? emp.getJobStep().getJobGrade().getName() : "No Grade";
            case "PAYGROUP":
                return emp.getPayGroup() != null ? emp.getPayGroup().getName() : "No Pay Group";
            default:
                return "Unknown";
        }
    }

    @PostMapping("/setup/allowance/update")
    public String updateAllowance(Allowance allowance, RedirectAttributes redirectAttributes) {
        if (allowance.getAmount() == null)
            allowance.setAmount(BigDecimal.ZERO);
        payrollSetupService.updateAllowance(allowance);
        redirectAttributes.addFlashAttribute("success", "Allowance updated successfully");
        return "redirect:/payroll/items";
    }

    @PostMapping("/setup/deduction/update")
    public String updateDeduction(Deduction deduction, RedirectAttributes redirectAttributes) {
        if (deduction.getAmount() == null)
            deduction.setAmount(BigDecimal.ZERO);
        payrollSetupService.updateDeduction(deduction);
        redirectAttributes.addFlashAttribute("success", "Deduction updated successfully");
        return "redirect:/payroll/items";
    }

    @PostMapping("/setup/taxrelief/update")
    public String updateTaxRelief(TaxRelief taxRelief, RedirectAttributes redirectAttributes) {
        if (taxRelief.getAmount() == null)
            taxRelief.setAmount(BigDecimal.ZERO);
        payrollSetupService.updateTaxRelief(taxRelief);
        redirectAttributes.addFlashAttribute("success", "Tax relief updated successfully");
        return "redirect:/payroll/items";
    }


    @GetMapping("/payroll/paye-pension-report")
    public String getPayePensionReport(@RequestParam(defaultValue = "GRADE") String groupBy, Model model) {
        if (authenticationManager.isRestrictedHr()) {
            return "redirect:/payroll/employee-payroll";
        }

        YearMonth currentMonth = YearMonth.now();

        final Set<Long> scopedIds;
        if (jobHrEmployeeAccessService.isJobHrScopedUser()) {
            Long actorGradeId = jobHrEmployeeAccessService.getLoggedInJobGradeId();
            scopedIds = employeeOnboardingService.getAllOnboardings().stream()
                    .filter(e -> e.getJobStep() != null
                            && e.getJobStep().getJobGrade() != null
                            && Objects.equals(e.getJobStep().getJobGrade().getId(), actorGradeId))
                    .map(Employee::getId)
                    .collect(Collectors.toSet());
        } else {
            scopedIds = null;
        }

        List<PaySlipDTO> paySlips = paySlipService.getCurrentPeriodPaySlips(1L);
        Map<Long, Employee> employeeMap = employeeOnboardingService.getAllOnboardings().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));

        Map<String, PayePensionGroupDTO> groupedData = new LinkedHashMap<>();
        BigDecimal grandPaye = BigDecimal.ZERO;
        BigDecimal grandEmpPension = BigDecimal.ZERO;
        BigDecimal grandEmployerPension = BigDecimal.ZERO;
        int grandEmployeeCount = 0;

        for (PaySlipDTO ps : paySlips) {
            if (scopedIds != null && !scopedIds.contains(ps.getEmployeeId())) continue;

            Employee emp = employeeMap.get(ps.getEmployeeId());
            if (emp == null) continue;

            String groupName = resolvePayItemsGroupName(emp, groupBy);
            PayePensionGroupDTO group = groupedData.computeIfAbsent(groupName, k -> PayePensionGroupDTO.builder()
                    .groupName(k)
                    .employees(new ArrayList<>())
                    .build());

            // 1. Initialize variables
            BigDecimal paye = BigDecimal.ZERO;
            BigDecimal empPension = ps.getPensionAmount() != null ? ps.getPensionAmount() : BigDecimal.ZERO;

            // 2. Scan lines for both PAYE and Pension fallbacks
            if (ps.getDeductions() != null) {
                for (PaySlipLineDTO deduction : ps.getDeductions()) {
                    String code = deduction.getCode() != null ? deduction.getCode().trim() : "";
                    String desc = deduction.getDescription() != null ? deduction.getDescription().toLowerCase() : "";
                    BigDecimal amount = deduction.getAmount() != null ? deduction.getAmount() : BigDecimal.ZERO;

                    if ("PAYE".equalsIgnoreCase(code)) {
                        paye = paye.add(amount);
                    }

                    // If summary pension field is empty, extract it dynamically from the line breakdown
                    if (empPension.compareTo(BigDecimal.ZERO) == 0) {
                        if ("PENSION".equalsIgnoreCase(code) || desc.contains("pension")) {
                            empPension = empPension.add(amount);
                        }
                    }
                }
            }

            // 3. Compute statutory Employer Pension (standard 10% vs 8% ratio breakdown)
            BigDecimal employerPension = empPension.multiply(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_UP);

            PayePensionLineDTO line = PayePensionLineDTO.builder()
                    .employeeName(ps.getEmployeeName())
                    .employeeId(ps.getEmployeeId())
                    .paye(paye)
                    .employeePension(empPension)
                    .employerPension(employerPension)
                    .tinNumber(emp.getTinNumber() != null ? emp.getTinNumber() : "—")
                    .rsaPin(emp.getRsaPin() != null ? emp.getRsaPin() : "—")
                    .pfa(emp.getPfa() != null ? emp.getPfa() : "—")
                    .build();

            group.getEmployees().add(line);
            group.setTotalPaye(group.getTotalPaye().add(paye));
            group.setTotalEmployeePension(group.getTotalEmployeePension().add(empPension));
            group.setTotalEmployerPension(group.getTotalEmployerPension().add(employerPension));
            group.setEmployeeCount(group.getEmployees().size());

            grandPaye = grandPaye.add(paye);
            grandEmpPension = grandEmpPension.add(empPension);
            grandEmployerPension = grandEmployerPension.add(employerPension);
            grandEmployeeCount++;
        }

        List<PayePensionGroupDTO> reportList = new ArrayList<>(groupedData.values());
        reportList.sort(Comparator.comparing(PayePensionGroupDTO::getGroupName));

        Map<String, Object> totals = Map.of(
                "totalGroups", reportList.size(),
                "totalEmployees", grandEmployeeCount,
                "totalPaye", grandPaye,
                "totalEmployeePension", grandEmpPension,
                "totalEmployerPension", grandEmployerPension
        );

        model.addAttribute("report", reportList);
        model.addAttribute("totals", totals);
        model.addAttribute("groupBy", groupBy);
        model.addAttribute("reportMonth", currentMonth.toString());
        model.addAttribute("title", "PAYE & Pension Report");
        model.addAttribute("subTitle", "Statutory deductions and pension scheme reporting");

        return "payroll/fragments/paye-pension";
    }


}