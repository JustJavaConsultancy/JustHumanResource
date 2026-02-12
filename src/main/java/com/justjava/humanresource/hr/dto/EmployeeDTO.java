package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmployeeDTO {

    Long id;
    String employeeNumber;
    String firstName;
    String lastName;
    String email;
    String phoneNumber;
    LocalDate dateOfHire;
    EmploymentStatus employmentStatus;
    RecordStatus status;
    Department department;
    JobStep jobStep;
    PayGroup payGroup;
    boolean payrollEnabled;
}
