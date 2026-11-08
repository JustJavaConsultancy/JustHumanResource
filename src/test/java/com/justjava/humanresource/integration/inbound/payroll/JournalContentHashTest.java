package com.justjava.humanresource.integration.inbound.payroll;

import com.justjava.humanresource.integration.inbound.payroll.util.JournalContentHash;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;




class JournalContentHashTest {
    @Test
    void matchesFixedInteroperabilityVectorRegardlessOfInputOrder() {
        List<JournalContentHash.Line> lines = List.of(
                new JournalContentHash.Line(
                        "5000", "Salary Expense", "DEBIT",
                        new BigDecimal("100.00"), new BigDecimal("0.00"), null),
                new JournalContentHash.Line(
                        "2100", "Net Pay Payable", "CREDIT",
                        new BigDecimal("0.00"), new BigDecimal("100.00"), null));

        assertThat(JournalContentHash.calculate(
                "PAY-2026-10-57",
                "ORIGINAL",
                "57",
                "2026-10-01",
                "2026-10-31",
                "NGN",
                "Payroll 2026-10",
                lines))
                .isEqualTo("6c803c3c25b74f5a6584cdae32e9018210088a06b8bfc1b19fff71c316144b8b");
    }
}
