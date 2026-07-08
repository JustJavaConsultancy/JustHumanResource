package com.justjava.humanresource.request.dto;

import com.justjava.humanresource.request.enums.ExpensePaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseReimbursementDetailView(
        String claimantName,
        String departmentName,
        LocalDate expenseStartDate,
        LocalDate expenseEndDate,
        String businessPurpose,
        ExpensePaymentMethod paymentMethod,
        String paymentMethodLabel,
        String currency,
        BigDecimal totalClaimAmount,
        List<ExpenseReimbursementItemView> expenseItems
) {
}
