package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;

import java.util.List;
import java.util.Optional;

/**
 * Builds the approval route for a submitted loan and starts / stops its Flowable process.
 * Steps of earlier submissions are kept as audit; every submission creates a fresh batch whose
 * sequenceNo restarts at 1.
 */
public interface LoanApprovalRouteService {

    String PROCESS_KEY = "employeeLoanApprovalProcess";
    /** Keycloak group names (as normalised by AuthenticationManager) used as Flowable candidate groups. */
    String HR_GROUP = "humanresource";
    String FINANCE_GROUP = "financialofficers";

    /**
     * Creates the approval steps from the route snapshotted on the application and starts the process.
     * The application must be SUBMITTED. The InitializeLoanApprovalDelegate moves it to its first pending
     * status and stores the workflow instance id.
     *
     * @return the Flowable process instance id
     * @throws IllegalStateException if a custom route resolves to no approvers (the submission is rolled back)
     */
    String startApproval(EmployeeLoanApplication application);

    /** Deletes the running process instance, if any, and clears the application's workflow instance id. */
    void cancelApproval(EmployeeLoanApplication application);

    /** Steps created by the most recent submission, in sequence order. */
    List<EmployeeLoanApprovalStep> getLatestAttemptSteps(Long loanApplicationId);

    /** First undecided step of the latest submission. */
    Optional<EmployeeLoanApprovalStep> getCurrentStep(Long loanApplicationId);

    /** The undecided step that follows the current one, if any. */
    Optional<EmployeeLoanApprovalStep> getNextStep(Long loanApplicationId);

    /** Id of the open Flowable task of the application's running process, if there is one. */
    Optional<String> findActiveTaskId(Long loanApplicationId);
}