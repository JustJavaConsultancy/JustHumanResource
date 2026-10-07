package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.hr.entity.EmployeeBankDetail;
import com.justjava.humanresource.hr.repository.EmployeeBankDetailRepository;
import com.justjava.humanresource.loan.dto.LoanBankDetailResponse;
import com.justjava.humanresource.loan.service.LoanBankDetailService;
import com.justjava.humanresource.loan.service.LoanEmployeeContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Read/validate only. Never saves or modifies bank details. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanBankDetailServiceImpl implements LoanBankDetailService {

    static final String INCOMPLETE_MESSAGE =
            "Complete bank details are required for loans paid outside payroll. "
                    + "Please update your bank details from your profile page before submitting.";

    private static final Pattern TEN_DIGITS = Pattern.compile("^\\d{10}$");

    private final EmployeeBankDetailRepository bankDetailRepository;
    private final LoanEmployeeContextService employeeContextService;

    @Override
    public LoanBankDetailResponse getActiveBankDetails(Long employeeId) {
        if (employeeId == null) {
            return build(null, null);
        }
        Optional<EmployeeBankDetail> detail = bankDetailRepository.findActiveByEmployeeId(employeeId);
        return build(employeeId, detail.orElse(null));
    }

    @Override
    public LoanBankDetailResponse getCurrentEmployeeBankDetails() {
        return getActiveBankDetails(employeeContextService.getCurrentEmployee().getId());
    }

    @Override
    public LoanBankDetailResponse requireCompleteBankDetails(Long employeeId) {
        LoanBankDetailResponse response = getActiveBankDetails(employeeId);
        if (!response.isComplete()) {
            throw new IllegalArgumentException(INCOMPLETE_MESSAGE
                    + " (Missing or invalid: " + String.join(", ", response.getMissingFields()) + ")");
        }
        return response;
    }

    @Override
    public boolean hasCompleteBankDetails(Long employeeId) {
        return getActiveBankDetails(employeeId).isComplete();
    }

    // ------------------------------------------------------------ helpers

    private LoanBankDetailResponse build(Long employeeId, EmployeeBankDetail detail) {
        String bankName = detail == null ? null : trimToNull(detail.getBankName());
        String accountName = detail == null ? null : trimToNull(detail.getAccountName());
        String accountNumber = detail == null ? null : trimToNull(detail.getAccountNumber());

        List<String> missing = new ArrayList<>();
        if (bankName == null) {
            missing.add("Bank name");
        }
        if (accountName == null) {
            missing.add("Account name");
        }
        if (accountNumber == null) {
            missing.add("Account number");
        } else if (!TEN_DIGITS.matcher(accountNumber).matches()) {
            missing.add("Account number (must be exactly 10 digits)");
        }

        return LoanBankDetailResponse.builder()
                .employeeId(employeeId)
                .bankName(bankName)
                .accountName(accountName)
                .accountNumber(accountNumber)
                .complete(missing.isEmpty())
                .missingFields(missing)
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
