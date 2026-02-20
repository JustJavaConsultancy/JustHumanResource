package com.justjava.humanresource.orgStructure.dto;

import com.justjava.humanresource.core.enums.RecordStatus;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompanyDTO {

    private Long id;
    private String name;
    private String code;
    private Long parentCompanyId;
    private RecordStatus status;
}
