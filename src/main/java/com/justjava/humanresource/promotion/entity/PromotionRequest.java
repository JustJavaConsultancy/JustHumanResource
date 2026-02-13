package com.justjava.humanresource.promotion.entity;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hr_promotion_request")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String processInstanceId;

    @ManyToOne
    private Employee employee;

    @ManyToOne
    private JobStep currentRole;

    @ManyToOne
    private JobStep proposedRole;

    private String justification;

    private BigDecimal appraisalScore;

    @Enumerated(EnumType.STRING)
    private PromotionStatus status;

    private LocalDate effectiveDate;

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
}
