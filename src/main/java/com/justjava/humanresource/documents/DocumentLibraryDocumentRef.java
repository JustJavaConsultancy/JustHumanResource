package com.justjava.humanresource.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentLibraryDocumentRef(
        @NotBlank String sourceType,
        @NotNull Long id
) {
}
