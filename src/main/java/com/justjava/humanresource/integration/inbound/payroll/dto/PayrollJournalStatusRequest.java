package com.justjava.humanresource.integration.inbound.payroll.dto;

import com.justjava.humanresource.integration.inbound.payroll.dto.PayrollJournalStatusRequest;

public record PayrollJournalStatusRequest(String status, Long amsJournalId, String reasonCode, String message) {
}
