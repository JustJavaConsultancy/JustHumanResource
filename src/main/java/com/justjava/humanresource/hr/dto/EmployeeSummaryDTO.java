package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import lombok.*;

        import java.io.Serializable;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String employeeNumber;
    private String fullName;
    private String email;
    private String phoneNumber;
    private EmploymentStatus employmentStatus;
    private RecordStatus status;

    private JobGradeSummaryDTO jobGrade;
    private JobStepSummaryDTO jobStep;
    private PayGroupSummaryDTO payGroup;
}
