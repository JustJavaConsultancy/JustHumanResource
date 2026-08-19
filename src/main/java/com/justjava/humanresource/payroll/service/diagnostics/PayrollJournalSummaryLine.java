package com.justjava.humanresource.payroll.service.diagnostics;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PayrollJournalSummaryLine {
    private String label;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;

    public BigDecimal getDifference() {
        return debitAmount.subtract(creditAmount);
    }
}
