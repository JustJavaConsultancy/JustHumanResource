package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filter shared by the Finance list endpoint and the CSV export, so both always return the same rows.
 * All fields are optional; null/blank means "no restriction". Date ranges are inclusive (whole days).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDisbursementFilter {

    private LoanDisbursementStatus status;
    private LoanDisbursementMethod method;

    /** Case-insensitive match on employee name, employee number or application number. */
    private String search;

    /** Case-insensitive match on department name. */
    private String department;

    private Long loanProductId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromFinalApprovedAt;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toFinalApprovedAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromPaidAt;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toPaidAt;
}
