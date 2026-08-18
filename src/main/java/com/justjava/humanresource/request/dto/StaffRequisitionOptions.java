package com.justjava.humanresource.request.dto;

import java.util.List;

public record StaffRequisitionOptions(
        List<RequestLookupOption> departments,
        List<RequestLookupOption> jobGrades,
        List<RequestLookupOption> employees,
        List<RequestEnumOption> employmentTypes,
        List<RequestEnumOption> requisitionReasons
) {
}
