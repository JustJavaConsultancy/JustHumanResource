package com.justjava.humanresource.integration.inbound.payroll.service.outbox;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.dto.ClaimedPayrollEvent;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationOutboxEvent;
import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationOutboxRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
public class OutboxClaimService {
    private final IntegrationOutboxRepository repository;
    private final IntegrationPayrollProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<ClaimedPayrollEvent> claimDue() {
        LocalDateTime now = LocalDateTime.now();
        List<IntegrationOutboxEvent> due = repository.findDueForClaim(now, properties.getBatchSize());
        List<ClaimedPayrollEvent> claims = new ArrayList<>(due.size());
        long leaseSeconds = Math.max(30, (properties.getConfirmTimeoutMs() * 2 + 999) / 1000);
        for (IntegrationOutboxEvent event : due) {
            LocalDateTime leaseUntil = now.plusSeconds(leaseSeconds);
            event.setStatus(OutboxStatus.PUBLISHING);
            event.setAttempts(event.getAttempts() + 1);
            event.setLeaseUntil(leaseUntil);
            repository.save(event);
            claims.add(new ClaimedPayrollEvent(
                    event.getEventId(),
                    event.getAggregateId(),
                    event.getExchangeName(),
                    event.getRoutingKey(),
                    event.getPayloadJson(),
                    correlationId(event.getPayloadJson()),
                    event.getAttempts(),
                    leaseUntil));
        }
        return claims;
    }

    private String correlationId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode value = root.get("correlationId");
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Stored payroll event envelope is invalid.", exception);
        }
    }
}
