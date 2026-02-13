package com.justjava.humanresource.payroll.workflow.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.core.exception.InvalidOperationException;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PayrollOrchestrationServiceImpl implements PayrollOrchestrationService {

    private final PayrollRunRepository payrollRunRepository;
    private final EmployeeRepository employeeRepository;

    /* =========================
     * INITIALIZE
     * ========================= */

    @Override
    @Transactional
    public Long initializePayrollRun(
            Long employeeId,
            LocalDate payrollDate,
            String processInstanceId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeId));


        PayrollRun run = new PayrollRun();
        run.setEmployee(employee); // IMPORTANT: ensure entity supports this
        run.setPayrollDate(payrollDate);
        run.setStatus(PayrollRunStatus.IN_PROGRESS);
        run.setFlowableProcessInstanceId(processInstanceId);

        return payrollRunRepository.save(run).getId();
    }

    /* =========================
     * CALCULATE EARNINGS
     * ========================= */

    @Override
    @Transactional
    public void calculateEarnings(Long payrollRunId) {

        PayrollRun run = getActiveRun(payrollRunId);

        ensureStatus(run, PayrollRunStatus.IN_PROGRESS);

        /*
         * Phase 3 + Phase 6 logic:
         * - Resolve pay group
         * - Base salary
         * - Allowances
         * - Employee overrides
         * - Persist PayrollLineItem entries
         */

        run.setStatus(PayrollRunStatus.IN_PROGRESS); // explicit for clarity
    }

    /* =========================
     * APPLY STATUTORY
     * ========================= */

    @Override
    @Transactional
    public void applyStatutoryDeductions(Long payrollRunId) {

        PayrollRun run = getActiveRun(payrollRunId);

        ensureStatus(run, PayrollRunStatus.IN_PROGRESS);

        /*
         * Phase 4 logic:
         * - Calculate PAYE
         * - Calculate Pension
         * - Add statutory line items
         */
    }

    /* =========================
     * FINALIZE
     * ========================= */

    @Override
    @Transactional
    public void finalizePayroll(Long payrollRunId) {

        PayrollRun run = getActiveRun(payrollRunId);

        ensureStatus(run, PayrollRunStatus.IN_PROGRESS);

        run.setStatus(PayrollRunStatus.POSTED);

        payrollRunRepository.save(run);
    }

    /* =========================
     * INTERNAL SAFETY
     * ========================= */

    private PayrollRun getActiveRun(Long payrollRunId) {
        return payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("PayrollRun", payrollRunId));
    }

    private void ensureStatus(PayrollRun run, PayrollRunStatus expected) {
        if (!run.getStatus().equals(expected)) {
            throw new InvalidOperationException(
                    "Invalid payroll state transition. Current status: "
                            + run.getStatus()
            );
        }
    }
}
