package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.core.enums.PayFrequency;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePayGroupCommand {

    private String code;
    private String name;
    private PayFrequency payFrequency;  // REQUIRED
    private Long parentId;              // optional
}
