package com.justjava.humanresource.request.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class FinanceRequestActionCommand {
    @Size(max = 2000)
    private String comment;
}