package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import com.justjava.humanresource.kpi.service.AppraisalService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import java.math.RoundingMode;

@Component("calculateKpiScoreDelegate")
@RequiredArgsConstructor
public class CalculateKpiScoreDelegate implements JavaDelegate {

    private final AppraisalService appraisalService;
    private final EmployeeRepository employeeRepository;
    private final AppraisalCycleRepository cycleRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");
        Long cycleId = (Long) execution.getVariable("cycleId");

        AppraisalCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow();

        // Only calculate KPI part here
        EmployeeAppraisal appraisal =
                appraisalService.generateAppraisal(
                        employeeId,
                        cycle,
                        null,  // managerScore not yet provided
                        null
                );

        execution.setVariable("appraisalId", appraisal.getId());
        execution.setVariable("kpiScore", appraisal.getKpiScore());
    }
}
