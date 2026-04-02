package com.justjava.humanresource.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BankTransferRequest {

    private String accountNumber;
    private String accountName;
    private String bankName;  //new
    private BigDecimal amount;
    private String reference;
}