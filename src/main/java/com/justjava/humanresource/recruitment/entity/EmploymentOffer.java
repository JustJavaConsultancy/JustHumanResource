package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.OfferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @Entity
@Table(name = "recruitment_employment_offers", uniqueConstraints =
        @UniqueConstraint(name = "uk_offer_application_version", columnNames = {"applicationId", "offerVersion"}))
public class EmploymentOffer extends BaseEntity {
    @Column(nullable = false) private Long applicationId;
    @Column(nullable = false) private Integer offerVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OfferStatus status = OfferStatus.DRAFT;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal annualGrossCompensation;
    @Column(nullable = false, length = 3) private String currency = "NGN";
    private Long jobStepId;
    private Long payGroupId;
    private LocalDate proposedStartDate;
    private LocalDate expiresOn;
    @Column(length = 3000) private String terms;
    private Long approvedByEmployeeId;
    private LocalDateTime approvedAt;
    private LocalDateTime sentAt;
    private LocalDateTime respondedAt;
    @Column(length = 100) private String workflowInstanceId;
}
