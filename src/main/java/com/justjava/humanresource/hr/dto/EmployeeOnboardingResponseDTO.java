package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmployeeOnboardingResponseDTO {

    private Long id;
    private Long employeeId;
    private String processInstanceId;
    private OnboardingStatus status;
    private String initiatedBy;
}
