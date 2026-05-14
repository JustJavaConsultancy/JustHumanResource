package com.justjava.humanresource.payroll.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentGatewayResolverTest {

    @Test
    void resolve_shouldReturnMatchingGateway_caseInsensitive() {
        PaymentGateway paystack = mock(PaymentGateway.class);
        PaymentGateway flutterwave = mock(PaymentGateway.class);
        when(paystack.getName()).thenReturn("PAYSTACK");
        when(flutterwave.getName()).thenReturn("Flutterwave");

        PaymentGatewayResolver resolver = new PaymentGatewayResolver(List.of(paystack, flutterwave));

        PaymentGateway resolved = resolver.resolve("paystack");

        assertEquals(paystack, resolved);
    }

    @Test
    void resolve_shouldThrowWhenGatewayIsMissing() {
        PaymentGateway paystack = mock(PaymentGateway.class);
        when(paystack.getName()).thenReturn("PAYSTACK");

        PaymentGatewayResolver resolver = new PaymentGatewayResolver(List.of(paystack));

        assertThrows(IllegalStateException.class, () -> resolver.resolve("unknown"));
    }
}
