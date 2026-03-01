package com.justjava.humanresource.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PayrollJournalEntryDTO {

    private Long id;
    private Long companyId;

    private Long payrollPeriodId;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private String accountCode;
    private String accountName;

    private BigDecimal debitAmount;
    private BigDecimal creditAmount;

    private String description;

    private boolean exported;
}