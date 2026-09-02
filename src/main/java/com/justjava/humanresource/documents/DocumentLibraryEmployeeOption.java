package com.justjava.humanresource.documents;

public record DocumentLibraryEmployeeOption(
        Long id,
        String employeeNumber,
        String fullName,
        String department,
        String email
) {
}
