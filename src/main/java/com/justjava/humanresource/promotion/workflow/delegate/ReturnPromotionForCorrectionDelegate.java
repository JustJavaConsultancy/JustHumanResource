package com.justjava.humanresource.promotion.workflow.delegate;

import com.justjava.humanresource.promotion.service.PromotionApprovalDecisionService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("returnPromotionForCorrectionDelegate")
@RequiredArgsConstructor
public class ReturnPromotionForCorrectionDelegate implements JavaDelegate {
    private final PromotionApprovalDecisionService decisionService;
    @Override public void execute(DelegateExecution execution) {
        Object actor = execution.getVariable("approvalActorId");
        decisionService.returnForCorrection(((Number) execution.getVariable("promotionRequestId")).longValue(),
                String.valueOf(execution.getVariable("approvalComment")), actor == null ? null : ((Number) actor).longValue());
    }
}
