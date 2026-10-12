package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.enums.PromotionApprovalDecision;
import com.justjava.humanresource.promotion.enums.PromotionApprovalStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "hr_promotion_approval_step",
        indexes = {
                @Index(name = "idx_promotion_step_request", columnList = "promotion_request_id"),
                @Index(name = "idx_promotion_step_decision", columnList = "decision")
        }
)
public class PromotionApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promotion_request_id")
    private PromotionRequest promotionRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionApprovalStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionApprovalDecision decision = PromotionApprovalDecision.PENDING;

    @ManyToOne
    private Employee approver;

    @ManyToOne
    private Employee originalApprover;

    @ManyToOne
    private Employee reassignedBy;

    @ManyToOne
    private Employee delegatedBy;

    @ManyToOne
    private Employee escalatedToApprover;

    @Column(length = 40)
    private String approverGroup;

    @Column(length = 80)
    private String escalatedToGroup;

    @Column(nullable = false)
    private Integer sequenceNumber;

    private LocalDateTime assignedAt;
    private LocalDateTime actedAt;
    private LocalDateTime reassignedAt;
    private LocalDateTime delegatedAt;
    private LocalDateTime lastReminderSentAt;
    private LocalDateTime escalatedAt;

    private Integer reminderCount = 0;

    private String flowableTaskId;

    @Column(length = 2000)
    private String comment;

    @Column(length = 2000)
    private String reassignmentComment;

    @Column(length = 2000)
    private String delegationComment;

    @Column(length = 2000)
    private String escalationComment;
}
