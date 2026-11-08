package com.justjava.humanresource.integration.inbound.payroll.dto;

import com.justjava.humanresource.integration.inbound.payroll.dto.PayrollJournalErrorResponse;

import java.util.List;

public record PayrollJournalErrorResponse(String code, String message, boolean retryable, List<String> details) {
}
