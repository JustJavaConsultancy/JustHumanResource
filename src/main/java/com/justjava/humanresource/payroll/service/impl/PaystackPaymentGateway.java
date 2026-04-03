package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.payroll.dto.BankTransferRequest;
import com.justjava.humanresource.payroll.dto.PaymentStatus;
import com.justjava.humanresource.payroll.service.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PaystackPaymentGateway implements PaymentGateway {

    private final RestTemplate restTemplate = new RestTemplate();
    private final BankCodeService bankCodeService;

    @Value("${paystack.secret.key}")
    private String secretKey;

    private static final String BASE_URL = "https://api.paystack.co";

    @Override
    public String getName() {
        return "PAYSTACK";
    }

    @Override
    public String initiateTransfer(BankTransferRequest request) {
        String recipientCode = createTransferRecipient(request);

        HttpHeaders headers = createHeaders();
        Map<String, Object> body = Map.of(
                "source", "balance",
                "amount", request.getAmount().multiply(BigDecimal.valueOf(100)).intValue(),
                "recipient", recipientCode,
                "reference", request.getReference()
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(BASE_URL + "/transfer", entity, Map.class);

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("transfer_code");
    }

    @Override
    public void initiateBulkTransfer(List<BankTransferRequest> requests) {
        HttpHeaders headers = createHeaders();


        List<Map<String, Object>> transfers = requests.stream().map(req ->
                Map.<String, Object>of(
                        "amount", req.getAmount().multiply(BigDecimal.valueOf(100)).intValue(),
                        "recipient", createTransferRecipient(req),
                        "reference", req.getReference()
                )
        ).collect(Collectors.toList());

        Map<String, Object> body = Map.of(
                "source", "balance",
                "currency", "NGN",
                "transfers", transfers
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(BASE_URL + "/transfer/bulk", entity, Map.class);

            System.out.println("DEBUG: Paystack Bulk Response: " + response.getBody());
        } catch (Exception e) {
            System.err.println("CRITICAL: Paystack Bulk Transfer Failed: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public PaymentStatus checkStatus(String reference) {
        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    BASE_URL + "/transfer/verify/" + reference,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            String status = (String) data.get("status");

            return switch (status.toLowerCase()) {
                case "success" -> PaymentStatus.SUCCESS;
                case "failed", "reversed" -> PaymentStatus.FAILED;
                default -> PaymentStatus.PROCESSING;
            };
        } catch (Exception e) {
            return PaymentStatus.PROCESSING;
        }
    }


    /** Paystack requires creating a 'recipient' before you can send money to a bank account. */

    private String createTransferRecipient(BankTransferRequest request) {
        // Check what name is coming from the DB
        System.out.println("DEBUG: Request Bank Name from DB: [" + request.getBankName() + "]");

        String bankCode = bankCodeService.getBankCode(request.getBankName());

        // Check what code the Service is returning
        System.out.println("DEBUG: Resolved Bank Code: [" + bankCode + "]");

        HttpHeaders headers = createHeaders();
        Map<String, Object> body = Map.of(
                "type", "nuban",
                "name", request.getAccountName(),
                "account_number", request.getAccountNumber(),
                "bank_code", bankCode,
                "currency", "NGN"
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // Log the full URL and Body
        System.out.println("DEBUG: Calling Paystack URL: " + BASE_URL + "/transferrecipient");
        System.out.println("DEBUG: Paystack Request Body: " + body);

        ResponseEntity<Map> response = restTemplate.postForEntity(BASE_URL + "/transferrecipient", entity, Map.class);

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return (String) data.get("recipient_code");
    }


    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(secretKey);
        return headers;
    }
}