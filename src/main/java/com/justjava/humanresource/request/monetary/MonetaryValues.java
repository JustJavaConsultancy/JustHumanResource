package com.justjava.humanresource.request.monetary;

import java.math.BigDecimal;
import java.util.*;

public final class MonetaryValues {
    public static final Set<String> CURRENCIES = Set.of("NGN","USD","EUR","GBP","GHS","KES","ZAR","JPY");
    public static final BigDecimal MAX = new BigDecimal("99999999999999999.99");
    private MonetaryValues() {}
    public static String currency(String input) {
        String value = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCIES.contains(value)) throw new IllegalArgumentException("Unsupported currency. Choose one of " + String.join(", ",new TreeSet<>(CURRENCIES)));
        return value;
    }
    public static BigDecimal amount(BigDecimal value, String currency, boolean allowZero) {
        String code = currency(currency);
        if (value == null || value.compareTo(BigDecimal.ZERO) < (allowZero ? 0 : 1) || value.compareTo(MAX)>0)
            throw new IllegalArgumentException("Amount must be " + (allowZero ? "non-negative" : "positive") + " and within the supported monetary limit.");
        if (value.stripTrailingZeros().scale()>Currency.getInstance(code).getDefaultFractionDigits())
            throw new IllegalArgumentException("Amount has unsupported precision for " + code + ". No automatic rounding is performed.");
        return value;
    }
}
