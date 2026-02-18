package com.justjava.humanresource.workflow.delegate.kpi;


import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.service.AppraisalService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component("finalizeAppraisalDelegate")
@RequiredArgsConstructor
public class FinalizeAppraisalDelegate implements JavaDelegate {

    private final AppraisalService appraisalService;

    @Override
    public void execute(DelegateExecution execution) {

        Long appraisalId = getLongVariable(execution, "appraisalId");
        BigDecimal managerScore = getBigDecimalVariable(execution, "managerScore");
        String managerComment = (String) execution.getVariable("managerComment");

        EmployeeAppraisal appraisal =
                appraisalService.finalizeAppraisal(
                        appraisalId,
                        managerScore,
                        managerComment
                );

        execution.setVariable("finalScore", appraisal.getFinalScore());
        execution.setVariable("appraisalOutcome", appraisal.getOutcome());
    }

    /* =====================================================
       SAFE VARIABLE EXTRACTION (Prevents ClassCastException)
       ===================================================== */

    private Long getLongVariable(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value == null) {
            throw new IllegalStateException("Missing process variable: " + name);
        }
        return Long.valueOf(value.toString());
    }

    private BigDecimal getBigDecimalVariable(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value == null) {
            throw new IllegalStateException("Missing process variable: " + name);
        }
        return new BigDecimal(value.toString());
    }
}
