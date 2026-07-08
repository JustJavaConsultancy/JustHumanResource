package com.justjava.humanresource.request.dto;

import com.justjava.humanresource.request.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseReimbursementItemView(
        LocalDate expenseDate,
        ExpenseCategory expenseCategory,
        String expenseCategoryLabel,
        String description,
        String vendorName,
        BigDecimal amount,
        String currency,
        String remarks
) {
}
