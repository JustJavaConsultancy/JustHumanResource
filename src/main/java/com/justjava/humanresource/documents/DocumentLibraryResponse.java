package com.justjava.humanresource.documents;

import java.util.List;

public record DocumentLibraryResponse(
        List<DocumentLibraryItemDTO> documents,
        long totalElements,
        int page,
        int size,
        int totalPages
) {
}
