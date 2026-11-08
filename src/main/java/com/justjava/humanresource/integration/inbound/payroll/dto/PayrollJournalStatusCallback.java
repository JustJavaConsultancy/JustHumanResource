package com.justjava.humanresource.integration.inbound.payroll.dto;

import com.justjava.humanresource.integration.inbound.payroll.dto.PayrollJournalStatusCallback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PayrollJournalStatusCallback(
        @NotBlank @Pattern(regexp = "POSTED|REJECTED") String status,
        Long amsJournalId,
        String reasonCode,
        String message) {
}
