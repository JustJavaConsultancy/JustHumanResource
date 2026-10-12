package com.justjava.humanresource.promotion.workflow.delegate;

import com.justjava.humanresource.promotion.service.PromotionApprovalDecisionService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("finalizePromotionRejectionDelegate")
@RequiredArgsConstructor
public class FinalizePromotionRejectionDelegate implements JavaDelegate {
    private final PromotionApprovalDecisionService decisionService;
    @Override public void execute(DelegateExecution execution) {
        decisionService.finalizeRejection(number(execution, "promotionRequestId"), text(execution, "approvalComment"),
                nullableNumber(execution, "approvalActorId"));
    }
    private Long number(DelegateExecution e, String key) { return ((Number) e.getVariable(key)).longValue(); }
    private Long nullableNumber(DelegateExecution e, String key) { Object v=e.getVariable(key); return v==null?null:((Number)v).longValue(); }
    private String text(DelegateExecution e, String key) { Object v=e.getVariable(key); return v==null?null:String.valueOf(v); }
}
