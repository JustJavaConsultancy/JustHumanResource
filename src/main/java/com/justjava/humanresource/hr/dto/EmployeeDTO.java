package com.justjava.humanresource.hr.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class EmployeeDTO {
    private Long id;
    private String employeeNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfHire;
    private String employmentStatus;   // or EmploymentStatus enum
    private String status;              // RecordStatus
    private Long departmentId;
    private Long jobStepId;
    private Long payGroupId;
    private boolean payrollEnabled;
    private boolean kpiEnabled;

    // Compulsory
    private String ninNumber;
    private String bvnNumber;

    // Next of Kin
    private String nextOfKinName;
    private String nextOfKinPhoneNumber;
    private String nextOfKinEmail;
    private String nextOfKinAddress;

    // Guarantor
    private String guarantorName;
    private String guarantorPhoneNumber;
    private String guarantorEmail;
    private String guarantorAddress;
    private String guarantorNinNumber;

    // Emergency contact fields (flat)
    private String emergencyContactName;
    private String emergencyRelationship;
    private String emergencyPhoneNumber;
    private String emergencyAlternativePhoneNumber;

    // Personal information fields
    private LocalDate dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String residentialAddress;
    private String mission;

    // Bank details fields (flat – current active bank)
    private String bankName;
    private String accountName;
    private String accountNumber;

    // We might also need an id to identify the bank detail record when updating (optional)
    private Long bankDetailId;
}