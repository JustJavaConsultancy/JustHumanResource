package com.justjava.humanresource.promotion.workflow.delegate;

import com.justjava.humanresource.promotion.service.PromotionApprovalDecisionService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("finalizePromotionApprovalDelegate")
@RequiredArgsConstructor
public class FinalizePromotionApprovalDelegate implements JavaDelegate {
    private final PromotionApprovalDecisionService decisionService;
    @Override public void execute(DelegateExecution execution) {
        decisionService.finalizeApproval(((Number) execution.getVariable("promotionRequestId")).longValue());
    }
}
