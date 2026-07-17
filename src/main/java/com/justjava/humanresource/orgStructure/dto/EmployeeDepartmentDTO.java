package com.justjava.humanresource.orgStructure.dto;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.dto.JobGradeSummaryDTO;
import com.justjava.humanresource.hr.dto.JobStepSummaryDTO;
import com.justjava.humanresource.hr.dto.PayGroupSummaryDTO;
import lombok.*;

        import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeDepartmentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private Long companyId;
    private Long parentDepartmentId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private RecordStatus status;

    private JobGradeSummaryDTO jobGrade;
    private JobStepSummaryDTO jobStep;
    private PayGroupSummaryDTO payGroup;
}