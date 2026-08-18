package com.justjava.humanresource.request.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class RequestApprovalActionCommand { @NotBlank private String taskId; private String comment; }
