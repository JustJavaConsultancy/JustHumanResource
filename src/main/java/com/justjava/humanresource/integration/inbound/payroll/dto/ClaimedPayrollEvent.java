package com.justjava.humanresource.integration.inbound.payroll.dto;


import java.time.LocalDateTime;
import java.util.UUID;

public record ClaimedPayrollEvent(
        UUID eventId,
        String aggregateId,
        String exchange,
        String routingKey,
        String payload,
        String correlationId,
        int attempts,
        LocalDateTime leaseUntil) {
}
