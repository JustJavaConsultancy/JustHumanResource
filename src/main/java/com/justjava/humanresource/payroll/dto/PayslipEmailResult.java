package com.justjava.humanresource.payroll.dto;

public record PayslipEmailResult(
        Long employeeId,
        String employeeName,
        String email,
        String status,
        String message,
        String messageId
) {
}
