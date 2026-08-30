package com.justjava.humanresource.recruitment.workflow.delegate;
import com.justjava.humanresource.recruitment.service.OfferService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
@Component("rejectEmploymentOfferDelegate") @RequiredArgsConstructor
public class RejectEmploymentOfferDelegate implements JavaDelegate {
    private final OfferService offerService;
    @Override public void execute(DelegateExecution execution) {
        offerService.reject(((Number) execution.getVariable("offerId")).longValue());
    }
}
