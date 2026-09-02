package com.justjava.humanresource.documents;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DocumentForwardCommand(
        @Valid List<DocumentLibraryDocumentRef> documents,
        List<Long> recipientEmployeeIds,
        boolean allEmployees,
        @Size(max = 2000) String message
) {
}
