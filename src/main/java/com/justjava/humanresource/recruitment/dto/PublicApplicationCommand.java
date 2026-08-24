package com.justjava.humanresource.recruitment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicApplicationCommand {
    @NotBlank private String firstName;
    private String middleName;
    @NotBlank private String lastName;
    @NotBlank @Email private String email;
    private String phone;
    private String address;
    private String linkedInUrl;
    private String portfolioUrl;
    private String source;
    @AssertTrue(message = "Candidate consent is required") private boolean consent;
}
