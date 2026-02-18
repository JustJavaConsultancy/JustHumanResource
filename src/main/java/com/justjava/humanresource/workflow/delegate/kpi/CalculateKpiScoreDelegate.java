package com.justjava.humanresource.workflow.delegate.kpi;


import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import com.justjava.humanresource.kpi.service.AppraisalService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("calculateKpiScoreDelegate")
@RequiredArgsConstructor
public class CalculateKpiScoreDelegate implements JavaDelegate {

    private final AppraisalService appraisalService;
    private final AppraisalCycleRepository cycleRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = getLongVariable(execution, "employeeId");
        Long cycleId = getLongVariable(execution, "cycleId");

        AppraisalCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() ->
                        new IllegalStateException("Appraisal cycle not found: " + cycleId)
                );

        // Create draft appraisal (KPI score only)
        EmployeeAppraisal appraisal =
                appraisalService.createDraftAppraisal(
                        employeeId,
                        cycle
                );

        execution.setVariable("appraisalId", appraisal.getId());
        execution.setVariable("kpiScore", appraisal.getKpiScore());
    }

    /* =====================================================
       SAFE VARIABLE EXTRACTION
       Prevents ClassCastException from Flowable Forms
       ===================================================== */

    private Long getLongVariable(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value == null) {
            throw new IllegalStateException("Missing process variable: " + name);
        }
        return Long.valueOf(value.toString());
    }
}
