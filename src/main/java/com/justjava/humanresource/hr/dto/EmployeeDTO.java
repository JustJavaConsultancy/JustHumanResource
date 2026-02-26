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

    // Emergency contact fields
    private String emergencyContactName;
    private String emergencyRelationship;
    private String emergencyPhoneNumber;
    private String emergencyAlternativePhoneNumber;

    // Personal information fields
    private LocalDate dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String residentialAddress;
    private String mission;   // new
}