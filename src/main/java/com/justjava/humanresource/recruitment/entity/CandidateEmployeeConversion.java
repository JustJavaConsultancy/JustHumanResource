package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name = "recruitment_candidate_employee_conversions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_conversion_application", columnNames = "applicationId"),
        @UniqueConstraint(name = "uk_conversion_employee", columnNames = "employeeId")})
public class CandidateEmployeeConversion extends BaseEntity {
    @Column(nullable = false) private Long applicationId;
    @Column(nullable = false) private Long candidateId;
    @Column(nullable = false) private Long employeeId;
    @Column(nullable = false) private Long onboardingId;
    @Column(nullable = false, length = 100) private String onboardingProcessInstanceId;
}
