package com.justjava.humanresource.payroll.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankCodeService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${paystack.secret.key}")
    private String secretKey;

    public String getBankCode(String bankName) {
        if (bankName == null || bankName.isEmpty()) return "058"; // Default to GTB

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            // Fetch all Nigerian banks from Paystack
            var response = restTemplate.exchange(
                    "https://api.paystack.co/bank?country=nigeria",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            List<Map<String, Object>> banks = (List<Map<String, Object>>) response.getBody().get("data");

            return banks.stream()
                    .filter(b -> b.get("name").toString().equalsIgnoreCase(bankName.trim()))
                    .map(b -> b.get("code").toString())
                    .findFirst()
                    .orElse("058"); // Fallback to GTB if no match found
        } catch (Exception e) {
            return "058";
        }
    }
}