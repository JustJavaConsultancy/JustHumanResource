package com.justjava.humanresource.onboarding.dto;

import lombok.Data;

import java.util.List;

@Data
public class StartEmployeeOnboardingCommand {

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;

    private Long departmentId;
    private Long jobStepId;
    private Long payGroupId;
    private Long managerId;

    // NEW
    private String tinNumber;
    private String rsaPin;
    private String pfa;
    private String ninNumber;
    private String bvnNumber;
    private String nextOfKinName;
    private String nextOfKinPhoneNumber;
    private String nextOfKinEmail;
    private String nextOfKinAddress;
    private String guarantorName;
    private String guarantorPhoneNumber;
    private String guarantorEmail;
    private String guarantorAddress;
    private String guarantorNinNumber;

    private List<String> groups;
}