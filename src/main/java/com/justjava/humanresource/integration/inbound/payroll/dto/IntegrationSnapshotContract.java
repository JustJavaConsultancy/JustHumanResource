package com.justjava.humanresource.integration.inbound.payroll.dto;

import com.justjava.humanresource.integration.inbound.payroll.dto.IntegrationSnapshotContract;
import com.justjava.humanresource.integration.inbound.payroll.dto.IntegrationSnapshotLine;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;

import java.time.LocalDate;
import java.util.List;

public record IntegrationSnapshotContract(
        String sourceSystem, String journalReference, String revision, String payrollPeriodId,
        LocalDate periodStart, LocalDate periodEnd, String currency, String narration,
        List<IntegrationSnapshotLine> lines, SnapshotStatus status, String contentHash) {
}
