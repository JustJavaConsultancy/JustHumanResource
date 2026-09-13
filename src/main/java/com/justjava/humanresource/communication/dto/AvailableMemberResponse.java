package com.justjava.humanresource.communication.dto;

public record AvailableMemberResponse(
        Long id,
        String fullName,
        String department
) {
}