package com.justjava.humanresource.hr.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeBankDetailDTO {

    private Long id;
    private Long employeeId;
    private String bankName;
    private String accountName;
    private String accountNumber;
    private String sortCode;
    private String branchCode;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private boolean primaryAccount;
}