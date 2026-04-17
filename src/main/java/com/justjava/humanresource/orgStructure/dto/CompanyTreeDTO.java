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

    private String logoBase64;
    private String logoContentType;

    private List<CompanyTreeDTO> subsidiaries;
}
