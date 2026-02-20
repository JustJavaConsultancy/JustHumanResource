package com.justjava.humanresource.orgStructure.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeNodeDTO {

    private Long employeeId;
    private String employeeNumber;
    private String fullName;
    private Long departmentId;

    private List<EmployeeNodeDTO> directReports;
}
