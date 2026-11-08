package com.justjava.humanresource.integration.inbound.payroll.entity;

import com.justjava.humanresource.integration.inbound.payroll.enums.OutboxStatus;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;




@Entity
@Table(name = "integration_outbox")
@Getter
@Setter
public class IntegrationOutboxEvent {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "event_version", nullable = false)
    private int eventVersion;
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;
    @Column(name = "routing_key", nullable = false, length = 150)
    private String routingKey;
    @Column(name = "exchange_name", nullable = false, length = 150)
    private String exchangeName;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;
    @Version
    private Long version;
}
