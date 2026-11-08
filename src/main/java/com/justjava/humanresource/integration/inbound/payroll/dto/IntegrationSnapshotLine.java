package com.justjava.humanresource.integration.inbound.payroll.dto;

import com.justjava.humanresource.integration.inbound.payroll.dto.IntegrationSnapshotLine;

import java.math.BigDecimal;

public record IntegrationSnapshotLine(
        String accountCode, String accountName, String lineType,
        BigDecimal debitAmount, BigDecimal creditAmount, String narration) {
}
