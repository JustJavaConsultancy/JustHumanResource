package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.core.enums.PayFrequency;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PayGroupResponseDTO {

    private Long id;
    private String code;
    private String name;
    private PayFrequency payFrequency;
    private Long parentId;
}

