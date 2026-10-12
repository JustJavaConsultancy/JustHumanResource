package com.justjava.humanresource.promotion.workflow.delegate;

import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionEligibilityService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("evaluatePromotionEligibilityDelegate")
@RequiredArgsConstructor
public class EvaluatePromotionEligibilityDelegate implements JavaDelegate {

    private final PromotionRequestRepository requestRepository;
    private final PromotionEligibilityService eligibilityService;

    @Override
    public void execute(DelegateExecution execution) {
        Long requestId = ((Number) execution.getVariable("promotionRequestId")).longValue();
        PromotionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Promotion request not found: " + requestId));
        eligibilityService.assertCanSubmit(request);
        execution.setVariable("eligibilityPassed", true);
    }
}
