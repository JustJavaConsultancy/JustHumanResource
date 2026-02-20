package com.justjava.humanresource.orgStructure.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentTreeDTO {

    private Long departmentId;
    private String name;
    private String code;

    private List<DepartmentTreeDTO> children;
}
