package com.justjava.humanresource.request.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.request.enums.FinanceNotificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Durable marker for the single "new request submitted" Finance notification of a request.
 *
 * The unique constraint on workflow_request_id is the one-notification-per-request invariant:
 * a second insert for the same request fails loudly instead of enqueuing a second email.
 * The row is inserted in the same transaction as the first submission (Step 7), and delivery
 * progress is recorded here after commit.
 */
@Getter @Setter @Entity
@Table(name = "workflow_request_finance_notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_finance_notification_request",
                columnNames = "workflowRequestId"))
public class WorkflowRequestFinanceNotification extends BaseEntity {

    @Column(nullable = false)
    private Long workflowRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinanceNotificationStatus status = FinanceNotificationStatus.PENDING;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer attemptCount = 0;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer recipientCount = 0;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer sentCount = 0;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer failedCount = 0;

    private LocalDateTime lastAttemptAt;
    private LocalDateTime sentAt;

    @Column(length = 2000)
    private String lastError;
}
