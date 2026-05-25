package com.justjava.humanresource.kpi.service;

import java.util.List;

/**
 * Summary returned after a CSV bulk-upload attempt.
 * The controller renders this as flash attributes / model attributes
 * so the Thymeleaf template can show a success/error banner.
 */
public record KpiCsvUploadResultDTO(
        int          savedCount,
        int          totalRows,
        List<String> rowErrors
) {
    public boolean hasErrors() {
        return rowErrors != null && !rowErrors.isEmpty();
    }

    public boolean isFullSuccess() {
        return savedCount == totalRows;
    }
}
