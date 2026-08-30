package com.justjava.humanresource.communication.dto;

public record EmployeeContactResponse(
        Long id,
        String employeeNumber,
        String fullName,
        String email,
        String department,
        boolean online,
        long unreadCount
) {
}
