package com.justjava.humanresource.payroll.workflow.impl;


import com.justjava.humanresource.common.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PayrollOrchestrationServiceImpl implements PayrollOrchestrationService {

    private final PayrollRunRepository payrollRunRepository;

    @Override
    public Long initializePayrollRun(
            Long employeeId,
            LocalDate payrollDate,
            String processInstanceId) {

        PayrollRun run = new PayrollRun();
        run.setPayrollDate(payrollDate);
        run.setStatus(PayrollRunStatus.IN_PROGRESS);
        run.setFlowableProcessInstanceId(processInstanceId);

        return payrollRunRepository.save(run).getId();
    }

    @Override
    public void calculateEarnings(Long payrollRunId) {
        // Phase 3 logic will populate PayrollLineItem
        // Allowances + base salary (real-time safe)
    }

    @Override
    public void applyStatutoryDeductions(Long payrollRunId) {
        // Phase 4 PAYE + Pension services used here
    }

    @Override
    public void finalizePayroll(Long payrollRunId) {
        PayrollRun run = payrollRunRepository
                .findById(payrollRunId)
                .orElseThrow();

        run.setStatus(PayrollRunStatus.POSTED);
        payrollRunRepository.save(run);
    }
}
