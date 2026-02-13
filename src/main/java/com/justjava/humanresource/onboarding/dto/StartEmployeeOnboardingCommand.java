package com.justjava.humanresource.onboarding.dto;

import lombok.Data;

@Data
public class StartEmployeeOnboardingCommand {

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;

    private Long departmentId;
    private Long jobStepId;
    private Long payGroupId;
}

