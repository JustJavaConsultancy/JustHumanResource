package com.justjava.humanresource.promotion.workflow.delegate;

import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("initializePromotionApprovalDelegate")
@RequiredArgsConstructor
public class InitializePromotionApprovalDelegate implements JavaDelegate {

    private final PromotionApprovalStepRepository stepRepository;

    @Override
    public void execute(DelegateExecution execution) {
        Long requestId = number(execution.getVariable("promotionRequestId"));
        List<PromotionApprovalStep> steps = stepRepository
                .findByPromotionRequest_IdOrderBySequenceNumberAsc(requestId).stream()
                .filter(step -> step.getDecision() == PromotionApprovalDecision.PENDING)
                .toList();
        if (steps.isEmpty()) throw new IllegalStateException("Promotion approval route is empty.");

        PromotionApprovalStep manager = find(steps, PromotionApprovalStage.LINE_MANAGER);
        execution.setVariable("requiresLineManagerApproval", manager != null);
        execution.setVariable("lineManagerEmployeeId",
                manager == null || manager.getApprover() == null ? null : manager.getApprover().getId().toString());
        execution.setVariable("requiresFinanceApproval", find(steps, PromotionApprovalStage.FINANCE) != null);
        execution.setVariable("requiresExecutiveApproval", find(steps, PromotionApprovalStage.EXECUTIVE) != null);
    }

    private PromotionApprovalStep find(List<PromotionApprovalStep> steps, PromotionApprovalStage stage) {
        return steps.stream().filter(step -> step.getStage() == stage).findFirst().orElse(null);
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }
}
