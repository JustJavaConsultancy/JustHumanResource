package com.justjava.humanresource.orgStructure.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompanyTreeDTO {

    private Long companyId;
    private String name;
    private String code;

    private List<CompanyTreeDTO> subsidiaries;
}
