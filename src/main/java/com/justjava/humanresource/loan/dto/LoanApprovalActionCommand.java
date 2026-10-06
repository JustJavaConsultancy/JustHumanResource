package com.justjava.humanresource.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload for approve / reject / return. The comment is optional for approve and mandatory for
 * reject and return (enforced in LoanApprovalServiceImpl).
 */
@Data
public class LoanApprovalActionCommand {

    @NotBlank
    private String taskId;

    @Size(max = 2000)
    private String comment;
}
