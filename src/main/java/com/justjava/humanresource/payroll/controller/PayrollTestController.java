package com.justjava.humanresource.payroll.controller;

import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
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
    private final PayGroupRepository payGroupRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollChangeOrchestrator payrollChangeOrchestrator;

    /* ============================================================
       EMPLOYEE CREATION
       ============================================================ */

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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
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
       JOB STEP CHANGE (SALARY CHANGE)
       ============================================================ */

    @PostMapping("/employee/{employeeId}/change-jobstep/{jobStepId}")
    public Employee changeJobStep(
            @PathVariable Long employeeId,
            @PathVariable Long jobStepId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate effectiveDate) {

        return employeeService.changeJobStep(
                employeeId,
                jobStepId,
                effectiveDate
        );
    }

    /* ============================================================
       MANUAL PAYROLL TRIGGER (FOR TESTING)
       ============================================================ */

    @PostMapping("/employee/{employeeId}/run-payroll")
    public String triggerPayroll(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
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

    /* ============================================================
       VIEW SINGLE PAYROLL RUN
       ============================================================ */

    @GetMapping("/run/{runId}")
    public PayrollRun getPayrollRun(@PathVariable Long runId) {
        return payrollRunRepository.findById(runId)
                .orElseThrow();
    }
}
/**
 🧪 What You Can Now Test
 1️⃣ Employee Onboarding
 POST /api/test/payroll/employee


 → Creates employee
 → Triggers SalaryChangedEvent
 → Starts Flowable process
 → Generates first PayrollRun
 → Generates first PaySlip

 2️⃣ Change Pay Group
 POST /api/test/payroll/employee/1/change-paygroup/3?effectiveDate=2026-02-01


 → Domain event
 → Message correlation
 → Payroll recalculated
 → New PayrollRun

 3️⃣ Change Salary (Job Step)
 POST /api/test/payroll/employee/1/change-jobstep/5?effectiveDate=2026-02-01


 → SalaryChangedEvent
 → Async payroll
 → New PayrollRun

 4️⃣ Manual Payroll Trigger
 POST /api/test/payroll/employee/1/run-payroll?payrollDate=2026-01-31


 → Retro payroll supported

 5️⃣ View Payroll Runs
 GET /api/test/payroll/employee/1/runs


 See all recalculations.
 * */