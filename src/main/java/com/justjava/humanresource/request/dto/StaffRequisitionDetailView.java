package com.justjava.humanresource.request.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StaffRequisitionDetailView(
        String jobTitle,
        String departmentName,
        String jobGradeName,
        Integer numberOfPositions,
        String employmentTypeLabel,
        String requisitionReasonLabel,
        LocalDate targetStartDate,
        boolean budgeted,
        BigDecimal estimatedMonthlyCost,
        String reasonForHire,
        String replacementEmployeeName
) {
}
