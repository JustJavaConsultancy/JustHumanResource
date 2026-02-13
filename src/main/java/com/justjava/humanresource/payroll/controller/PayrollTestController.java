package com.justjava.humanresource.payroll.controller;


import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    public Employee changePayGroup(
            @PathVariable Long employeeId,
            @PathVariable Long payGroupId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate effectiveDate) {

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
    public Employee changeJobStep(
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
    /* ============================================================
       JOB STEP CHANGE
       ============================================================ */

    @PostMapping("/employee/{employeeId}/change-status")
    public Employee changeStatus(
            @PathVariable Long employeeId) {

        return employeeService.changeEmploymentStatus(
                employeeId, EmploymentStatus.ACTIVE,
                LocalDate.now()
        );
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
}
