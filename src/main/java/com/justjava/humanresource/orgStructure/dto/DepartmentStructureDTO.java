package com.justjava.humanresource.orgStructure.dto;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.dto.EmployeeSummaryDTO;

import lombok.*;

        import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentStructureDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private Long parentDepartmentId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private RecordStatus status;

    private List<EmployeeSummaryDTO> employees;
}
