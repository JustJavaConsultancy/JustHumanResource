package com.justjava.humanresource.promotion.workflow.delegate;

import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.service.PromotionApprovalDecisionService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("processPromotionApprovalDecisionDelegate")
@RequiredArgsConstructor
public class ProcessPromotionApprovalDecisionDelegate implements JavaDelegate {

    private final PromotionApprovalDecisionService decisionService;

    @Override
    public void execute(DelegateExecution execution) {
        decisionService.record(
                number(execution.getVariable("promotionRequestId")),
                PromotionApprovalStage.valueOf(String.valueOf(execution.getVariable("approvalStage"))),
                String.valueOf(execution.getVariable("approvalDecision")),
                text(execution.getVariable("approvalComment")),
                nullableNumber(execution.getVariable("approvalActorId")),
                text(execution.getVariable("flowableTaskId")));
    }

    private Long number(Object value) { return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value)); }
    private Long nullableNumber(Object value) { return value == null ? null : number(value); }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
}
