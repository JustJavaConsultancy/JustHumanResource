package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "hr_promotion_activity_log",
        indexes = @Index(name = "idx_promotion_activity_request", columnList = "promotion_request_id")
)
public class PromotionActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promotion_request_id")
    private PromotionRequest promotionRequest;

    @ManyToOne
    private Employee actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PromotionActivityType activityType;

    @Column(length = 2000)
    private String description;

    @Column(length = 80)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 120)
    private String deviceMetadata;

    private LocalDateTime createdAt;
}
