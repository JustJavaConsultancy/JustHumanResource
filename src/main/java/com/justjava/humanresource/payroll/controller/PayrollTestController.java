package com.justjava.humanresource.payroll.controller;


import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.dto.EmployeePositionHistoryDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/test/payroll")
@RequiredArgsConstructor
public class PayrollTestController {

    private final EmployeeService employeeService;
    private final EmployeeOnboardingService employeeOnboardingService;
    private final PayGroupRepository payGroupRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollChangeOrchestrator payrollChangeOrchestrator;
    private final PayrollSetupService payrollSetupService;
    private final PayrollPeriodService payrollPeriodService;

    private final EmployeePositionHistoryService employeePositionHistoryService;
    /* ============================================================
       PAYROLL SETUP SECTION
       ============================================================ */

    @PostMapping("/setup/paye-band")
    public PayeTaxBand createPayeBand(@RequestBody PayeTaxBand band) {
        return payrollSetupService.createPayeTaxBand(band);
    }

    @GetMapping("/setup/paye-band")
    public List<PayeTaxBand> getActivePayeBands(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return payrollSetupService.getActivePayeBands(date);
    }

    @PostMapping("/setup/pension-scheme")
    public PensionScheme createPensionScheme(@RequestBody PensionScheme scheme) {
        return payrollSetupService.createPensionScheme(scheme);
    }

    @GetMapping("/setup/pension-schemes")
    public List<PensionScheme> getActivePensionSchemes() {
        return payrollSetupService.getActivePensionSchemes();
    }

    /* ============================================================
   ALLOWANCE SETUP
   ============================================================ */

    @PostMapping("/setup/allowance")
    public Allowance createAllowance(@RequestBody Allowance allowance) {
        return payrollSetupService.createAllowance(allowance);
    }

    @GetMapping("/setup/allowances")
    public List<Allowance> getAllowances() {
        return payrollSetupService.getActiveAllowances();
    }

/* ============================================================
   DEDUCTION SETUP
   ============================================================ */

    @PostMapping("/setup/deduction")
    public Deduction createDeduction(@RequestBody Deduction deduction) {
        return payrollSetupService.createDeduction(deduction);
    }

    @GetMapping("/setup/deductions")
    public List<Deduction> getDeductions() {
        return payrollSetupService.getActiveDeductions();
    }

    @GetMapping("/setup/validate")
    public String validatePayrollReadiness(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate payrollDate) {

        payrollSetupService.validatePayrollSystemReadiness(payrollDate);

        return "Payroll system ready for date: " + payrollDate;
    }

    /* ============================================================
       EMPLOYEE ONBOARDING (FLOWABLE PROCESS)
       ============================================================ */

    @PostMapping("/onboarding")
    public EmployeeOnboardingResponseDTO startOnboarding(
            @RequestBody StartEmployeeOnboardingCommand command,
            @RequestParam(defaultValue = "testUser") String initiatedBy) {

        return employeeOnboardingService.startOnboarding(
                command,
                initiatedBy
        );
    }

    @PostMapping("/employee")
    public Employee createEmployee(@RequestBody EmployeeDTO employee) {
        return employeeService.createEmployee(employee);
    }

    @GetMapping("/employee/{employeeNumber}")
    public Employee getEmployee(@PathVariable String employeeNumber) {
        return employeeService.getByEmployeeNumber(employeeNumber);
    }

    /* ============================================================
       PAY GROUP CHANGE
       ============================================================ */

    @PostMapping("/employee/{employeeId}/change-paygroup/{payGroupId}")
    public EmployeeDTO changePayGroup(
            @PathVariable Long employeeId,
            @PathVariable Long payGroupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate) {

        PayGroup payGroup = payGroupRepository.findById(payGroupId)
                .orElseThrow();

        return employeeService.changePayGroup(
                employeeId,
                payGroup,
                effectiveDate
        );
    }

    /* ============================================================
       JOB STEP CHANGE
       ============================================================ */

    @PostMapping("/employee/{employeeId}/change-jobstep/{jobStepId}")
    public EmployeeDTO changeJobStep(
            @PathVariable Long employeeId,
            @PathVariable Long jobStepId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate effectiveDate) {

        return employeeService.changeJobStep(
                employeeId,
                jobStepId,
                effectiveDate
        );
    }

    @GetMapping("/positions/active")
    public List<EmployeePositionHistoryDTO> getActivePositions() {
        return employeePositionHistoryService.getActivePositions();
    }
    @GetMapping("/employee/{employeeId}/position/current")
    public EmployeePositionHistoryDTO getCurrentPosition(
            @PathVariable Long employeeId) {
        return employeePositionHistoryService
                .getCurrentPositionAPI(employeeId);
    }

    /* ============================================================
       JOB STEP CHANGE
       ============================================================ */

    @PostMapping("/employee/{employeeId}/change-status")
    public EmployeeDTO changeStatus(
            @PathVariable Long employeeId) {

        return employeeService.changeEmploymentStatus(
                employeeId, EmploymentStatus.ACTIVE,
                LocalDate.now()
        );
    }
        /* ============================================================
       TRIGGER PAYROLL PERIOD OPENING
       ============================================================ */

    @PostMapping("/open_period")
    public PayrollPeriod openPeriod() {
        return payrollPeriodService.openInitialPeriod(1L,
                LocalDate.now(),LocalDate.now().plusMonths(1));
    }


    /* ============================================================
       MANUAL PAYROLL TRIGGER
       ============================================================ */

    @PostMapping("/employee/{employeeId}/run-payroll")
    public String triggerPayroll(
            @PathVariable Long employeeId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate payrollDate) {

        payrollChangeOrchestrator.recalculateForEmployee(
                employeeId,
                payrollDate
        );

        return "Payroll recalculation triggered for employee " + employeeId;
    }

    /* ============================================================
       VIEW PAYROLL RUNS
       ============================================================ */

    @GetMapping("/employee/{employeeId}/runs")
    public List<PayrollRun> getPayrollRuns(@PathVariable Long employeeId) {
        return payrollRunRepository.findAll()
                .stream()
                .filter(run -> run.getEmployee().getId().equals(employeeId))
                .toList();
    }

    @GetMapping("/run/{runId}")
    public PayrollRun getPayrollRun(@PathVariable Long runId) {
        return payrollRunRepository.findById(runId)
                .orElseThrow();
    }
    /* ============================================================
   BULK EMPLOYEE SETUP
   ============================================================ */

    @PostMapping("/setup/employee/{employeeId}/allowances")
    public List<EmployeeAllowanceResponse> attachAllowancesToEmployee(
            @PathVariable Long employeeId,
            @RequestBody List<AllowanceAttachmentRequest> requests) {

        return payrollSetupService.addAllowancesToEmployee(
                employeeId,
                requests
        );
    }

    @PostMapping("/setup/employee/{employeeId}/deductions")
    public List<EmployeeDeductionResponse> attachDeductionsToEmployee(
            @PathVariable Long employeeId,
            @RequestBody List<DeductionAttachmentRequest> requests) {

        return payrollSetupService.addDeductionsToEmployee(
                employeeId,
                requests
        );
    }
/* ============================================================
   BULK PAYGROUP SETUP
   ============================================================ */

    @PostMapping("/setup/paygroup/{payGroupId}/allowances")
    public List<PayGroupAllowanceResponse> attachAllowancesToPayGroup(
            @PathVariable Long payGroupId,
            @RequestBody List<AllowanceAttachmentRequest> requests) {

        return payrollSetupService.addAllowancesToPayGroup(
                payGroupId,
                requests
        );
    }

    @PostMapping("/setup/paygroup/{payGroupId}/deductions")
    public List<PayGroupDeductionResponse> attachDeductionsToPayGroup(
            @PathVariable Long payGroupId,
            @RequestBody List<DeductionAttachmentRequest> requests) {

        return payrollSetupService.addDeductionsToPayGroup(
                payGroupId,
                requests
        );
    }

}

/*
{
        "firstName": "Kazeem",
        "lastName": "Akinrinde",
        "email": "kazeem.akinrinde@gmail.com",
        "phoneNumber": "08012345678",
        "employmentStatus": "ONBOARDING",
        "departmentId": 1,
        "jobStepId": 2,
        "payGroupId": 1
        }
      */
