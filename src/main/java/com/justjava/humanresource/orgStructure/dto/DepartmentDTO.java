package com.justjava.humanresource.orgStructure.dto;

import com.justjava.humanresource.core.enums.RecordStatus;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentDTO {

    private Long id;
    private String code;
    private String name;
    private Long companyId;
    private Long parentDepartmentId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private RecordStatus status;
}
