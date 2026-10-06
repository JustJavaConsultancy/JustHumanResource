package com.justjava.humanresource.loan.workflow.delegate;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Marks the application REJECTED. The decision activity was recorded by the stage delegate. */
@Component("finalizeLoanRejectionDelegate")
@RequiredArgsConstructor
public class FinalizeLoanRejectionDelegate implements JavaDelegate {

    private final EmployeeLoanApplicationRepository applications;

    @Override
    public void execute(DelegateExecution execution) {
        Long id = ((Number) execution.getVariable("loanApplicationId")).longValue();
        Long actor = ((Number) execution.getVariable("approvalActorId")).longValue();
        EmployeeLoanApplication app = applications.findById(id)
                .orElseThrow(() -> new IllegalStateException("Loan application not found: " + id));

        app.setStatus(LoanApplicationStatus.REJECTED);
        app.setRejectedAt(LocalDateTime.now());
        app.setRejectedByEmployeeId(actor);
        applications.save(app);
    }
}
