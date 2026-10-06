package com.justjava.humanresource.loan.workflow.delegate;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.service.LoanActivityService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Sends the application back to the employee. The workflow instance id is cleared so the employee can
 * edit and resubmit, which creates a fresh batch of approval steps; earlier steps stay as audit.
 */
@Component("returnLoanForCorrectionDelegate")
@RequiredArgsConstructor
public class ReturnLoanForCorrectionDelegate implements JavaDelegate {

    private final EmployeeLoanApplicationRepository applications;
    private final LoanActivityService activityService;

    @Override
    public void execute(DelegateExecution execution) {
        Long id = ((Number) execution.getVariable("loanApplicationId")).longValue();
        Long actor = ((Number) execution.getVariable("approvalActorId")).longValue();
        String comment = (String) execution.getVariable("approvalComment");
        EmployeeLoanApplication app = applications.findById(id)
                .orElseThrow(() -> new IllegalStateException("Loan application not found: " + id));

        app.setStatus(LoanApplicationStatus.RETURNED_FOR_CORRECTION);
        app.setWorkflowInstanceId(null);
        applications.save(app);

        activityService.record(id, LoanActivityType.APPLICATION_RETURNED,
                "Returned for correction." + (comment == null || comment.isBlank() ? "" : " Comment: " + comment.trim()),
                actor);
    }
}
