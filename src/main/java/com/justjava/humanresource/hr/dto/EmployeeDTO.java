package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
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

    Long departmentId;   // ✅ instead of Department
    Long jobStepId;      // ✅ instead of JobStep
    Long payGroupId;     // ✅ instead of PayGroup

    boolean payrollEnabled;
}
