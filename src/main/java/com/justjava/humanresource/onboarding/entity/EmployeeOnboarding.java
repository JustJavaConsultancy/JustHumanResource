package com.justjava.humanresource.onboarding.entity;

import com.justjava.humanresource.common.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "employee_onboarding")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeOnboarding extends BaseEntity {

    private String processInstanceId;

    @OneToOne
    private Employee employee;

    @Enumerated(EnumType.STRING)
    private OnboardingStatus status;
    // INITIATED, HR_VERIFIED, DOCS_VERIFIED, APPROVED, COMPLETED, REJECTED

    private String initiatedBy;

    private LocalDateTime completedAt;
}

