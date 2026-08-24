package com.justjava.humanresource.recruitment.workflow.delegate;
import com.justjava.humanresource.recruitment.service.OfferService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
@Component("approveEmploymentOfferDelegate") @RequiredArgsConstructor
public class ApproveEmploymentOfferDelegate implements JavaDelegate {
    private final OfferService offerService;
    @Override public void execute(DelegateExecution execution) {
        Object approver = execution.getVariable("approverEmployeeId");
        offerService.approve(((Number) execution.getVariable("offerId")).longValue(), approver == null ? null : Long.valueOf(approver.toString()));
    }
}
