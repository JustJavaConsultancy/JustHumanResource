package com.justjava.humanresource.payroll.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentGatewayResolver {

    private final List<PaymentGateway> gateways;

    public PaymentGateway resolve(String name) {

        return gateways.stream()
                .filter(g -> g.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No gateway found"));
    }
}