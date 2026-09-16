package com.justjava.humanresource.mobile;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.kpi.dto.AppraisalTaskViewDTO;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.service.AppraisalService;
import com.justjava.humanresource.kpi.service.KpiAssignmentService;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import com.justjava.humanresource.kpi.service.KpiMeasurementService;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.dto.FutureEmployeeAllowanceDTO;
import com.justjava.humanresource.payroll.entity.PaySlipDTO;
import com.justjava.humanresource.payroll.service.PaySlipService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.Optional;

import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import com.justjava.humanresource.payroll.report.dto.ComponentBreakdownDTO;
import com.justjava.humanresource.payroll.report.dto.ComponentTrendDTO;
import com.justjava.humanresource.payroll.report.dto.PayeReportDTO;
import com.justjava.humanresource.payroll.report.dto.PensionReportDTO;
import com.justjava.humanresource.payroll.report.dto.PayrollSummaryDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mobile")
public class MobileEmployeeController {


    @Autowired
    private SetupService setupService;

    @Autowired
    private EmployeeOnboardingService employeeOnboardingService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PayrollSetupService payrollSetupService;

    @Autowired
    AuthenticationManager authenticationManager;


    @Autowired
    AppraisalService appraisalService;

    @Autowired
    private FlowableTaskService flowableTaskService;

    @Autowired
    private PaySlipService paySlipService;

    @Autowired
    private PayrollPeriodService payrollPeriodService;

    @Autowired
    private PayrollRunService payrollRunService;


    @GetMapping("/employees")
    public String getMobileEmployees(Model model) {
        List<Employee> employees = employeeOnboardingService.getAllOnboardings().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
        List<Department>         departments = setupService.getAllDepartments();
        List<PayGroup>           payGroups   = payrollSetupService.getAllPayGroups();
        List<JobGradeResponseDTO> jobGrades  = setupService.getAllJobGrades();
        model.addAttribute("employees",   employees);
        model.addAttribute("departments", departments);
        model.addAttribute("payGroups",   payGroups);
        model.addAttribute("jobGrades",   jobGrades);
        model.addAttribute("managerMap",  new java.util.LinkedHashMap<>());
        model.addAttribute("employeeCreationAllowed", true);
        model.addAttribute("employeeCreationBlockedReason", null);
        model.addAttribute("title",    "Employee Management");
        model.addAttribute("subTitle", "Manage employee records and data");
        return "mobile/employee-management";
    }

    @GetMapping("/requests")
    public String getMobileAdminRequests(Model model) {
        model.addAttribute("title",    "Requests");
        model.addAttribute("subTitle", "Organisation-wide request desk");
        return "mobile/request-admin";
    }

    @GetMapping("/requests/userGuide")
    public String getMobileAdminRequestsUserGuide(Model model) {
        model.addAttribute("title",    "Request Workflow User Guide");
        model.addAttribute("subTitle", "How to create, submit, approve, and track requests");
        return "mobile/request-admin-userGuide";
    }

    @GetMapping("/requests/{id}")
    public String getMobileAdminRequestDetail(Model model) {
        model.addAttribute("title",    "Request Detail");
        model.addAttribute("subTitle", "Review request details, approvals, comments, and attachments");
        return "mobile/request-admin-detail";
    }

    @GetMapping("/biometric/bootstrap")
    public String biometricBootstrap(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/mobile/employee/dashboard";
        }
        return "redirect:/oauth2/authorization/keycloak-mobile";
    }

    @PostMapping("/onboarding")
    public String startMobileOnboarding(
            StartEmployeeOnboardingCommand command,
            @RequestParam(defaultValue = "mobileHR") String initiatedBy) {
        EmployeeOnboardingResponseDTO employeeOnboardingResponseDTO = employeeOnboardingService.startOnboarding(
                command,
                initiatedBy
        );
        employeeService.changeEmploymentStatus(
                employeeOnboardingResponseDTO.getId(),
                EmploymentStatus.ACTIVE, LocalDate.now());

        return "redirect:/mobile/employees";
    }

    @GetMapping("/employee/dashboard")
    public String getMobileEmployeeDashboard(Model model) {
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PaySlipDTO latestPaySlip = paySlipService.getCurrentPeriodPaySlipForEmployee(1L, loginEmployee.getId());
        List<PaySlipDTO> previousPaySlip = paySlipService.getPaySlipsByEmployee(loginEmployee.getId());

        List<FutureEmployeeAllowanceDTO> futureAllowances = List.of();
        try {
            futureAllowances = payrollSetupService.getFutureAllowancesForEmployee(loginEmployee.getId());
        } catch (Exception ignored) {}

        // Build recent activity from real payslip and allowance data
        List<Map<String, String>> recentActivity = new ArrayList<>();
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMMM yyyy");
        DateTimeFormatter dateFmt  = DateTimeFormatter.ofPattern("d MMM yyyy");

        if (previousPaySlip != null) {
            previousPaySlip.stream().limit(3).forEach(slip -> {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("icon", "account_balance_wallet");
                item.put("description", "Payslip for " + slip.getPayDate().format(labelFmt) + " processed");
                item.put("timeAgo", slip.getPayDate().format(dateFmt));
                recentActivity.add(item);
            });
        }
        for (FutureEmployeeAllowanceDTO fa : futureAllowances) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("icon", "history_toggle_off");
            item.put("description", fa.getAllowanceName() + " scheduled to start");
            item.put("timeAgo", "From " + fa.getEffectiveFrom().format(labelFmt));
            recentActivity.add(item);
        }

        model.addAttribute("previousPaySlips", previousPaySlip);
        model.addAttribute("futureAllowances", futureAllowances);
        model.addAttribute("employee", employee);
        model.addAttribute("latestPaySlip", latestPaySlip);
        model.addAttribute("recentActivity", recentActivity);
        model.addAttribute("title", "Dashboard");
        model.addAttribute("subTitle", "Your personal overview");
        return "mobile/dashboard";
    }

    @GetMapping("/employee/profile")
    public String getMobileEmployeeProfile(Model model) {
        String email = (String) authenticationManager.get("email");
        // implement your own logic
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        PaySlipDTO latestPaySlip = paySlipService.getCurrentPeriodPaySlipForEmployee(1l,loginEmployee.getId());
        System.out.println("Latest Pay Slip: " + latestPaySlip);
        System.out.println("Logged in employee: " + loginEmployee);
        model.addAttribute("employee", employee);
        model.addAttribute("latestPaySlip", latestPaySlip);
        model.addAttribute("title", "My Profile");
        model.addAttribute("subTitle", "View and update your information");
        return "mobile/profile";
    }

    @GetMapping("/employee/promotions")
    public String getMobilePromotions(Model model) {
        model.addAttribute("title", "Promotions");
        model.addAttribute("subTitle", "View promotion history");
        return "mobile/promotions";
    }

    @GetMapping("/employee/payroll")
    public String getMobilePayroll(Model model) {

        String email = (String) authenticationManager.get("email");
        // implement your own logic
        Employee loginEmployee = employeeService.getByEmail(email);
        PaySlipDTO latestPaySlip = paySlipService.getCurrentPeriodPaySlipForEmployee(1l,loginEmployee.getId());

        List<PaySlipDTO> previousPaySlip = paySlipService.getPaySlipsByEmployee(loginEmployee.getId());
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());

        List<FutureEmployeeAllowanceDTO> futureAllowances = List.of();
        try {
            futureAllowances = payrollSetupService.getFutureAllowancesForEmployee(loginEmployee.getId());
        } catch (Exception ignored) {}

        System.out.println("Latest Pay Slip: " );
        model.addAttribute("previousPaySlips", previousPaySlip);
        model.addAttribute("previousPaySlip", previousPaySlip);
        model.addAttribute("latestPaySlip", latestPaySlip);
        model.addAttribute("employee", employee);
        model.addAttribute("futureAllowances", futureAllowances);
        model.addAttribute("title", "Payroll");
        model.addAttribute("subTitle", "View your pay stubs and benefits");
        return "mobile/payroll";
    }

    @GetMapping("/employee/leave")
    public String getMobileLeave(Model model) {
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());
        model.addAttribute("employee", employee);
        model.addAttribute("title", "Leave");
        model.addAttribute("subTitle", "Manage your time off");
        return "mobile/leave";
    }

    @GetMapping("/employee/performance")
    public String getMobilePerformance(Model model) {
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        // 🔹 COMPLETED PROCESSES
        List<HistoricProcessInstance> completedProcesses =
                flowableTaskService.getCompletedProcessInstancesForAssignee(
                        "employeeAppraisalProcess"
                );

        // 🔹 ACTIVE TASKS
        List<FlowableTaskDTO> tasks =
                flowableTaskService.getTasksForAssignee(
                        String.valueOf(loginEmployee.getId()),
                        "employeeAppraisalProcess"
                );
        List<AppraisalTaskViewDTO> enrichedAppraisals = new ArrayList<>();

        for (FlowableTaskDTO task : tasks) {

            Map<String, Object> variables = task.getVariables();

            if (variables.containsKey("appraisalId")) {

                Long appraisalId =
                        Long.valueOf(variables.get("appraisalId").toString());

                Optional<EmployeeAppraisal> appraisalOpt =
                        appraisalService.findAppraisalById(appraisalId);
                if (appraisalOpt.isEmpty()) {
                    System.out.println("Skipping orphaned task, appraisalId not found: " + appraisalId);
                    continue;
                }
                enrichedAppraisals.add(
                        new AppraisalTaskViewDTO(task, appraisalOpt.get())
                );
            }
        }
        enrichedAppraisals.forEach(
                appraisal -> System.out.println("Task: " + appraisal.getTask()+ ", Appraisal: " + appraisal.getAppraisal())
        );
        List<EmployeeAppraisal> employeeAppraisals = appraisalService.findAppraisalByEmployeeID(loginEmployee.getId());
        // existing
        model.addAttribute("tasks", tasks);
        model.addAttribute("employeeAppraisals", employeeAppraisals);

// add this
        model.addAttribute("appraisalMap",
                employeeAppraisals.stream()
                        .collect(Collectors.toMap(EmployeeAppraisal::getId, ea -> ea))
        );

        model.addAttribute("title", "Performance");
        model.addAttribute("subTitle", "View your KPI metrics");
        return "mobile/kpi";
    }

    @GetMapping("/employee/documents")
    public String getMobileDocuments(Model model) {
        String email = (String) authenticationManager.get("email");
        Employee loginEmployee = employeeService.getByEmail(email);
        model.addAttribute("employee", loginEmployee);
        model.addAttribute("title", "Documents");
        model.addAttribute("subTitle", "Manage your documents");
        return "mobile/documents";
    }

    @GetMapping("/employee/requests")
    public String getMobileRequests(Model model) {
        model.addAttribute("title", "My Requests");
        model.addAttribute("subTitle", "Workflow requests");
        return "mobile/request-main";
    }

    @GetMapping("/employee/requests/userGuide")
    public String getMobileRequestUserGuide(Model model) {
        model.addAttribute("title", "Request Guide");
        model.addAttribute("subTitle", "How to use requests");
        return "mobile/request-userGuide";
    }

    @GetMapping("/employee/requests/{id}")
    public String getMobileRequestDetail(@PathVariable Long id, Model model) {
        model.addAttribute("requestId", id);
        model.addAttribute("title", "Request Detail");
        model.addAttribute("subTitle", "Request #" + id);
        return "mobile/request-detail";
    }

    // ──────────────────────────────────────────────────────────────
    //  PAYROLL ADMIN ROUTES
    // ──────────────────────────────────────────────────────────────

    @GetMapping("/payroll/admin")
    public String getMobilePayrollAdmin(Model model) {
        List<Allowance> allowances = payrollSetupService.getActiveAllowances();
        List<Deduction> deductions = payrollSetupService.getActiveDeductions();
        List<Allowance> taxableAllowances = allowances.stream().filter(Allowance::isTaxable).toList();
        List<Deduction> saturatoryDeductions = deductions.stream().filter(Deduction::isStatutory).toList();
        List<com.justjava.humanresource.hr.entity.PayGroup> payGroups = payrollSetupService.getAllPayGroups();
        List employees = employeeOnboardingService.getAllOnboardings();

        PayrollPeriod currentPeriod = payrollPeriodService.getCurrentPeriod(1L);
        model.addAttribute("payrollStatus", currentPeriod != null ? currentPeriod.getStatus() : null);
        model.addAttribute("currentPayrollPeriod", currentPeriod);
        model.addAttribute("allowances", allowances.size());
        model.addAttribute("taxableAllowances", taxableAllowances.size());
        model.addAttribute("deductions", deductions.size());
        model.addAttribute("saturatoryDeductions", saturatoryDeductions.size());
        model.addAttribute("payGroups", payGroups.size());
        model.addAttribute("employees", employees.size());
        model.addAttribute("title", "Payroll Admin");
        model.addAttribute("subTitle", "Manage payroll settings and periods");
        return "mobile/payroll-admin";
    }

    @GetMapping("/payroll/historical")
    public String getMobileHistoricalAdjustments(Model model) {
        model.addAttribute("employees", employeeOnboardingService.getAllOnboardings());
        model.addAttribute("closedPeriods", payrollPeriodService.getClosedPeriods(1L));
        model.addAttribute("title", "Historical Adjustments");
        model.addAttribute("subTitle", "Versioned corrections for closed payroll periods");
        return "mobile/payroll-historical";
    }

    // ──────────────────────────────────────────────────────────────
    //  PAYROLL REPORT ROUTES
    // ──────────────────────────────────────────────────────────────

    private static final Long REPORT_COMPANY_ID = 1L;

    @GetMapping("/reporting")
    public String getMobileReporting(Model model) {
        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate endDate   = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        List<PayrollSummaryDTO> payrollSummary =
                payrollRunService.getPayrollSummary(REPORT_COMPANY_ID, startDate, endDate);
        model.addAttribute("payrollSummary", payrollSummary);

        BigDecimal grandTotalGross = payrollSummary.stream()
                .map(s -> s.getTotalGross() != null ? s.getTotalGross() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grandTotalNet = payrollSummary.stream()
                .map(s -> s.getTotalNet() != null ? s.getTotalNet() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grandTotalPaye = payrollSummary.stream()
                .map(s -> s.getTotalPaye() != null ? s.getTotalPaye() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grandTotalPension = payrollSummary.stream()
                .map(s -> s.getTotalPension() != null ? s.getTotalPension() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maxGroupGross = payrollSummary.stream()
                .map(s -> s.getTotalGross() != null ? s.getTotalGross() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);

        model.addAttribute("grandTotalGross",   grandTotalGross);
        model.addAttribute("grandTotalNet",     grandTotalNet);
        model.addAttribute("grandTotalPaye",    grandTotalPaye);
        model.addAttribute("grandTotalPension", grandTotalPension);
        model.addAttribute("maxGroupGross",     maxGroupGross);

        List<ComponentBreakdownDTO> earningsBreakdown =
                payrollRunService.getEarningsBreakdown(REPORT_COMPANY_ID, startDate, endDate);
        BigDecimal maxEarning = earningsBreakdown.stream()
                .map(e -> e.getTotalAmount() != null ? e.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        model.addAttribute("earningsBreakdown", earningsBreakdown);
        model.addAttribute("maxEarning", maxEarning);

        List<ComponentBreakdownDTO> deductionBreakdown =
                payrollRunService.getDeductionBreakdown(REPORT_COMPANY_ID, startDate, endDate);
        BigDecimal maxDeduction = deductionBreakdown.stream()
                .map(d -> d.getTotalAmount() != null ? d.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        model.addAttribute("deductionBreakdown", deductionBreakdown);
        model.addAttribute("maxDeduction", maxDeduction);

        List<ComponentTrendDTO> componentTrend = payrollRunService.getComponentTrend(REPORT_COMPANY_ID);
        List<String> trendPeriods = componentTrend.stream()
                .map(ComponentTrendDTO::getPeriod).filter(p -> p != null)
                .distinct().sorted().collect(Collectors.toList());
        List<String> trendCodes = componentTrend.stream()
                .map(ComponentTrendDTO::getComponentCode).filter(c -> c != null)
                .distinct().collect(Collectors.toList());
        BigDecimal maxTrendAmount = componentTrend.stream()
                .map(t -> t.getTotalAmount() != null ? t.getTotalAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        model.addAttribute("componentTrend",  componentTrend);
        model.addAttribute("trendPeriods",    trendPeriods);
        model.addAttribute("trendCodes",      trendCodes);
        model.addAttribute("maxTrendAmount",  maxTrendAmount);

        List<PayeReportDTO> payeReport =
                payrollRunService.getPayeReport(REPORT_COMPANY_ID, startDate, endDate);
        List<PensionReportDTO> pensionReport =
                payrollRunService.getPensionReport(REPORT_COMPANY_ID, startDate, endDate);
        model.addAttribute("recentEntries",        payeReport.stream().limit(5).collect(Collectors.toList()));
        model.addAttribute("totalEmployees",       payeReport.size());
        model.addAttribute("pensionEmployeeCount", pensionReport.size());
        model.addAttribute("reportYear", LocalDate.now().getYear());
        model.addAttribute("title",      "Reporting");
        model.addAttribute("subTitle",   "Payroll reporting overview");
        return "mobile/payroll-reporting";
    }

    @GetMapping("/master-report")
    public String getMobileMasterReport(Model model) {
        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate endDate   = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        List<PayrollSummaryDTO> payrollSummary =
                payrollRunService.getPayrollSummary(REPORT_COMPANY_ID, startDate, endDate);
        model.addAttribute("payrollSummary", payrollSummary);

        double grandTotalGross = payrollSummary.stream()
                .mapToDouble(s -> s.getTotalGross().doubleValue()).sum();
        double grandTotalDeductions = payrollSummary.stream()
                .mapToDouble(s -> s.getTotalDeductions().doubleValue()).sum();
        double grandTotalNet = payrollSummary.stream()
                .mapToDouble(s -> s.getTotalNet().doubleValue()).sum();
        model.addAttribute("grandTotalGross",      grandTotalGross);
        model.addAttribute("grandTotalDeductions", grandTotalDeductions);
        model.addAttribute("grandTotalNet",        grandTotalNet);

        List<ComponentBreakdownDTO> earningsBreakdown =
                payrollRunService.getEarningsBreakdown(REPORT_COMPANY_ID, startDate, endDate);
        double maxEarnings = earningsBreakdown.stream()
                .mapToDouble(c -> c.getTotalAmount().doubleValue()).max().orElse(1.0);
        model.addAttribute("earningsBreakdown", earningsBreakdown);
        model.addAttribute("maxEarnings",       maxEarnings);

        List<ComponentBreakdownDTO> deductionBreakdown =
                payrollRunService.getDeductionBreakdown(REPORT_COMPANY_ID, startDate, endDate);
        model.addAttribute("deductionBreakdown", deductionBreakdown);

        List<ComponentTrendDTO> componentTrend = payrollRunService.getComponentTrend(REPORT_COMPANY_ID);
        List<String> trendPeriods = componentTrend.stream()
                .map(ComponentTrendDTO::getPeriod).distinct().sorted().collect(Collectors.toList());
        List<String> trendComponents = componentTrend.stream()
                .map(ComponentTrendDTO::getComponentCode).distinct().collect(Collectors.toList());
        double maxTrendAmount = componentTrend.stream()
                .mapToDouble(c -> c.getTotalAmount().doubleValue()).max().orElse(1.0);
        model.addAttribute("componentTrend",  componentTrend);
        model.addAttribute("trendPeriods",    trendPeriods);
        model.addAttribute("trendComponents", trendComponents);
        model.addAttribute("maxTrendAmount",  maxTrendAmount);

        model.addAttribute("payeReport",    payrollRunService.getPayeReport(REPORT_COMPANY_ID, startDate, endDate));
        model.addAttribute("pensionReport", payrollRunService.getPensionReport(REPORT_COMPANY_ID, startDate, endDate));
        model.addAttribute("reportYear", LocalDate.now().getYear());
        model.addAttribute("title",      "Master Report");
        model.addAttribute("subTitle",   "Complete payroll master report");
        return "mobile/payroll-master-report";
    }

    @GetMapping("/paye-report")
    public String getMobilePayeReport(Model model) {
        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate endDate   = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        List<PayeReportDTO> payeReport =
                payrollRunService.getPayeReport(REPORT_COMPANY_ID, startDate, endDate);
        model.addAttribute("payeReport",    payeReport);
        model.addAttribute("totalRecords",  payeReport.size());

        BigDecimal totalTaxableIncome = payeReport.stream()
                .map(p -> p.getTaxableIncome() != null ? p.getTaxableIncome() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaye = payeReport.stream()
                .map(p -> p.getPaye() != null ? p.getPaye() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalYtdPaye = payeReport.stream()
                .map(p -> p.getYtdPaye() != null ? p.getYtdPaye() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalTaxableIncome", totalTaxableIncome);
        model.addAttribute("totalPaye",          totalPaye);
        model.addAttribute("totalYtdPaye",       totalYtdPaye);
        model.addAttribute("reportYear", LocalDate.now().getYear());
        model.addAttribute("title",      "PAYE Report");
        model.addAttribute("subTitle",   "PAYE tax deduction summary");
        return "mobile/payroll-paye-report";
    }

    @GetMapping("/pension-report")
    public String getMobilePensionReport(Model model) {
        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate endDate   = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        List<PensionReportDTO> pensionReport =
                payrollRunService.getPensionReport(REPORT_COMPANY_ID, startDate, endDate);
        model.addAttribute("pensionReport", pensionReport);

        BigDecimal totalEmployeeContribution = pensionReport.stream()
                .map(p -> p.getEmployeeContribution() != null ? p.getEmployeeContribution() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEmployerContribution = pensionReport.stream()
                .map(p -> p.getEmployerContribution() != null ? p.getEmployerContribution() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLiability = totalEmployeeContribution.add(totalEmployerContribution);
        model.addAttribute("totalEmployeeContribution", totalEmployeeContribution);
        model.addAttribute("totalEmployerContribution", totalEmployerContribution);
        model.addAttribute("totalLiability",            totalLiability);
        model.addAttribute("totalRecords",              pensionReport.size());
        model.addAttribute("reportYear", LocalDate.now().getYear());
        model.addAttribute("title",      "Pension Report");
        model.addAttribute("subTitle",   "Pension contribution summary");
        return "mobile/payroll-pension-report";
    }

    @GetMapping("/variance-report")
    public String getMobileVarianceReport(Model model) {
        model.addAttribute("title",    "Variance Report");
        model.addAttribute("subTitle", "Period-on-period payroll variance");
        return "mobile/payroll-variance";
    }
}
