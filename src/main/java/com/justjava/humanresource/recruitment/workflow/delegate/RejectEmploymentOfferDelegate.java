package com.justjava.humanresource.recruitment.workflow.delegate;
import com.justjava.humanresource.recruitment.enums.OfferStatus;
import com.justjava.humanresource.recruitment.repository.EmploymentOfferRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
@Component("rejectEmploymentOfferDelegate") @RequiredArgsConstructor
public class RejectEmploymentOfferDelegate implements JavaDelegate {
    private final EmploymentOfferRepository repository;
    @Override public void execute(DelegateExecution execution) {
        var offer = repository.findById(((Number) execution.getVariable("offerId")).longValue()).orElseThrow();
        offer.setStatus(OfferStatus.REJECTED); repository.save(offer);
    }
}
