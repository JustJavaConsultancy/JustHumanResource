package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.LoanBankDetailResponse;

/**
 * Read/validate-only access to employee bank details for the loan module.
 * Bank details are owned by EmployeeBankDetail (profile / HR employee management); this service never writes them.
 */
public interface LoanBankDetailService {

    /**
     * Active bank details for the employee, with completeness flags. Never throws for missing or
     * incomplete data: the response carries {@code complete=false} and the {@code missingFields}.
     */
    LoanBankDetailResponse getActiveBankDetails(Long employeeId);

    /** Same as {@link #getActiveBankDetails(Long)} for the logged-in employee. */
    LoanBankDetailResponse getCurrentEmployeeBankDetails();

    /**
     * Returns the bank details only when complete and valid.
     *
     * @throws IllegalArgumentException when anything is missing or invalid (message is safe to show to the user)
     */
    LoanBankDetailResponse requireCompleteBankDetails(Long employeeId);

    boolean hasCompleteBankDetails(Long employeeId);
}
