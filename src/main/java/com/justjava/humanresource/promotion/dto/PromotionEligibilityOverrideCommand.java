package com.justjava.humanresource.promotion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromotionEligibilityOverrideCommand {

    @NotBlank
    private String reason;
}
