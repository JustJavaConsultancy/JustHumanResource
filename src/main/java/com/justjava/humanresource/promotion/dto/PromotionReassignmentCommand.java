package com.justjava.humanresource.promotion.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromotionReassignmentCommand {

    @NotNull
    private Long approverId;

    private String comment;
}
