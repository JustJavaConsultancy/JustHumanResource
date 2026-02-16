package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.enums.AppraisalOutcome;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Component("finalizeAppraisalDelegate")
@RequiredArgsConstructor
public class FinalizeAppraisalDelegate implements JavaDelegate {

    private final EmployeeAppraisalRepository appraisalRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long appraisalId = (Long) execution.getVariable("appraisalId");
        BigDecimal managerScore =
                (BigDecimal) execution.getVariable("managerScore");

        EmployeeAppraisal appraisal =
                appraisalRepository.findById(appraisalId)
                        .orElseThrow();

        BigDecimal kpiScore = appraisal.getKpiScore();

        BigDecimal finalScore =
                kpiScore.multiply(BigDecimal.valueOf(0.7))
                        .add(managerScore.multiply(BigDecimal.valueOf(0.3)));

        appraisal.setManagerScore(managerScore);
        appraisal.setFinalScore(finalScore);
        appraisal.setOutcome(determineOutcome(finalScore));
        appraisal.setCompletedAt(LocalDateTime.now());

        appraisalRepository.save(appraisal);

        execution.setVariable("appraisalOutcome", appraisal.getOutcome());
    }

    private AppraisalOutcome determineOutcome(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(85)) >= 0)
            return AppraisalOutcome.EXCEEDS_EXPECTATION;
        if (score.compareTo(BigDecimal.valueOf(70)) >= 0)
            return AppraisalOutcome.MEETS_EXPECTATION;
        if (score.compareTo(BigDecimal.valueOf(50)) >= 0)
            return AppraisalOutcome.NEEDS_IMPROVEMENT;
        return AppraisalOutcome.UNDERPERFORMING;
    }
}
