package com.justjava.humanresource.request.dto;

import java.util.List;

public record ExpenseReimbursementOptions(
        List<RequestEnumOption> expenseCategories,
        List<RequestEnumOption> paymentMethods
) {
}
