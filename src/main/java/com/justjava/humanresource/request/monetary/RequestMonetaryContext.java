package com.justjava.humanresource.request.monetary;
import java.math.BigDecimal;
public record RequestMonetaryContext(BigDecimal amount,String currency) {
    public RequestMonetaryContext {
        currency=MonetaryValues.currency(currency);
        amount=MonetaryValues.amount(amount,currency,false);
    }
}
