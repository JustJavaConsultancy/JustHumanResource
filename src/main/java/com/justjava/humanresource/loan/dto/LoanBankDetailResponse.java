package com.justjava.humanresource.loan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Read-only view of an employee's active bank details, as used by the loan module.
 * {@code complete} is true only when bank name, account name and a 10-digit account number are all present.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanBankDetailResponse {

    private Long employeeId;
    private String bankName;
    private String accountName;
    private String accountNumber;

    /** True when every required field is present and valid. */
    private boolean complete;

    /** Human-readable names of the fields that are missing or invalid; empty when {@code complete}. */
    private List<String> missingFields;
}
