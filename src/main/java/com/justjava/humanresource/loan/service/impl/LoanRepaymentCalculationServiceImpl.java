package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.loan.dto.LoanRepaymentPreviewRequest;
import com.justjava.humanresource.loan.dto.LoanRepaymentPreviewResponse;
import com.justjava.humanresource.loan.dto.LoanRepaymentScheduleLineResponse;
import com.justjava.humanresource.loan.entity.LoanProduct;
import com.justjava.humanresource.loan.enums.LoanInterestType;
import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import com.justjava.humanresource.loan.repository.LoanProductRepository;
import com.justjava.humanresource.loan.service.LoanRepaymentCalculationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly repayment maths.
 *
 * Interest is flat-rate on the original principal for the selected tenor:
 * interest = principal x annualRate% x tenorMonths / 12.
 * Installments are the employee's repayment amount; the last one is the remainder.
 */
@Service
@RequiredArgsConstructor
public class LoanRepaymentCalculationServiceImpl implements LoanRepaymentCalculationService {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    /** 100 (percent) x 12 (months per year). */
    private static final BigDecimal PERCENT_MONTHS = BigDecimal.valueOf(1200);

    private final LoanProductRepository loanProductRepository;

    @Override
    @Transactional(readOnly = true)
    public LoanRepaymentPreviewResponse preview(LoanRepaymentPreviewRequest request) {
        if (request == null || request.getLoanProductId() == null) {
            throw new IllegalArgumentException("Loan product is required.");
        }
        LoanProduct product = loanProductRepository.findById(request.getLoanProductId())
                .orElseThrow(() -> new EntityNotFoundException("Loan product not found: " + request.getLoanProductId()));
        return preview(product, request.getRequestedAmount(), request.getRepaymentAmount(),
                request.getTenorMonths(), request.getRepaymentStartMonth());
    }

    @Override
    public LoanRepaymentPreviewResponse preview(LoanProduct product,
                                                BigDecimal requestedAmount,
                                                BigDecimal repaymentAmount,
                                                Integer tenorMonths,
                                                LocalDate repaymentStartMonth) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (isPositive(requestedAmount)) {
            if (requestedAmount.compareTo(product.getMinimumAmount()) < 0) {
                errors.add("Requested amount cannot be less than " + fmt(product.getMinimumAmount())
                        + " for this loan product.");
            }
            if (requestedAmount.compareTo(product.getMaximumAmount()) > 0) {
                errors.add("Requested amount cannot be more than " + fmt(product.getMaximumAmount())
                        + " for this loan product.");
            }
        }
        if (isPositive(repaymentAmount)
                && repaymentAmount.compareTo(product.getMinimumRepaymentAmount()) < 0) {
            errors.add("Repayment amount cannot be less than " + fmt(product.getMinimumRepaymentAmount())
                    + " per month for this loan product.");
        }
        if (tenorMonths != null && tenorMonths >= 1
                && tenorMonths > product.getMaximumTenorMonths()) {
            errors.add("Tenor cannot be more than " + product.getMaximumTenorMonths()
                    + " months for this loan product.");
        }
        if (repaymentStartMonth != null
                && repaymentStartMonth.withDayOfMonth(1).isBefore(LocalDate.now().withDayOfMonth(1))) {
            errors.add("Repayment start month cannot be in the past.");
        }

        Computation c = compute(product.getInterestType(), product.getInterestRate(),
                requestedAmount, repaymentAmount, tenorMonths, repaymentStartMonth, errors, warnings);
        return toResponse(c, product.getId(), product.getName(),
                product.getInterestType(), product.getInterestRate());
    }

    @Override
    public LoanRepaymentPreviewResponse validateOrThrow(LoanProduct product,
                                                        BigDecimal requestedAmount,
                                                        BigDecimal repaymentAmount,
                                                        Integer tenorMonths,
                                                        LocalDate repaymentStartMonth) {
        LoanRepaymentPreviewResponse response =
                preview(product, requestedAmount, repaymentAmount, tenorMonths, repaymentStartMonth);
        if (!response.isValid()) {
            throw new IllegalArgumentException(String.join(" ", response.getErrors()));
        }
        return response;
    }

    @Override
    public LoanRepaymentPreviewResponse calculate(LoanInterestType interestType,
                                                  BigDecimal interestRate,
                                                  BigDecimal requestedAmount,
                                                  BigDecimal repaymentAmount,
                                                  Integer tenorMonths,
                                                  LocalDate repaymentStartMonth) {
        Computation c = compute(interestType, interestRate, requestedAmount, repaymentAmount,
                tenorMonths, repaymentStartMonth, new ArrayList<>(), new ArrayList<>());
        return toResponse(c, null, null, interestType, interestRate);
    }

    // ------------------------------------------------------------------ core maths

    private Computation compute(LoanInterestType interestType,
                                BigDecimal interestRate,
                                BigDecimal requestedIn,
                                BigDecimal repaymentIn,
                                Integer tenor,
                                LocalDate startIn,
                                List<String> errors,
                                List<String> warnings) {

        boolean requestedOk = checkMoney(requestedIn, "Requested amount", errors);
        boolean repaymentOk = checkMoney(repaymentIn, "Repayment amount", errors);
        boolean tenorOk = tenor != null && tenor >= 1;
        if (!tenorOk) {
            errors.add("Tenor must be at least 1 month.");
        }
        if (startIn == null) {
            errors.add("Repayment start month is required.");
        }

        BigDecimal requested = requestedOk ? money(requestedIn) : requestedIn;
        BigDecimal repayment = repaymentOk ? money(repaymentIn) : repaymentIn;
        LocalDate start = startIn == null ? null : startIn.withDayOfMonth(1);

        BigDecimal totalInterest = null;
        BigDecimal total = null;
        BigDecimal minRequired = null;
        if (requestedOk && tenorOk) {
            totalInterest = interestFor(interestType, interestRate, requested, tenor);
            total = requested.add(totalInterest);
            minRequired = total.divide(BigDecimal.valueOf(tenor), 2, RoundingMode.UP);
            if (repaymentOk && repayment.compareTo(minRequired) < 0) {
                errors.add("A repayment of " + fmt(repayment) + " per month over " + tenor
                        + " month(s) will not clear the total of " + fmt(total)
                        + ". The minimum monthly repayment for this tenor is " + fmt(minRequired) + ".");
            }
        }

        Integer installments = null;
        BigDecimal finalInstallment = null;
        LocalDate endMonth = null;
        List<LoanRepaymentScheduleLineResponse> lines = new ArrayList<>();

        if (errors.isEmpty()) {
            int n = total.divide(repayment, 0, RoundingMode.UP).intValueExact();
            finalInstallment = total.subtract(repayment.multiply(BigDecimal.valueOf(n - 1L)))
                    .setScale(2, ROUNDING);
            installments = n;
            endMonth = start.plusMonths(n - 1L);

            BigDecimal cumulativePaid = BigDecimal.ZERO;
            BigDecimal cumulativeInterest = BigDecimal.ZERO;
            for (int i = 1; i <= n; i++) {
                BigDecimal amount = (i == n) ? finalInstallment : repayment;
                cumulativePaid = cumulativePaid.add(amount);
                // Spread interest in proportion to what has been paid; the last row takes the remainder
                // so portions always add up exactly to totalInterest.
                BigDecimal targetInterest = (i == n)
                        ? totalInterest
                        : totalInterest.multiply(cumulativePaid).divide(total, 2, ROUNDING);
                BigDecimal interestPortion = targetInterest.subtract(cumulativeInterest);
                cumulativeInterest = targetInterest;

                lines.add(LoanRepaymentScheduleLineResponse.builder()
                        .sequenceNumber(i)
                        .dueMonth(start.plusMonths(i - 1L))
                        .expectedAmount(amount)
                        .principalPortion(amount.subtract(interestPortion))
                        .interestPortion(interestPortion)
                        .paidAmount(BigDecimal.ZERO.setScale(2))
                        .outstandingAmount(amount)
                        .status(LoanRepaymentStatus.PENDING)
                        .build());
            }

            if (n < tenor) {
                warnings.add("The loan will be fully repaid in " + n + " installment(s), earlier than the selected "
                        + tenor + "-month tenor.");
                if (totalInterest.signum() > 0) {
                    warnings.add("Interest is calculated on the selected tenor, so repaying sooner does not reduce it.");
                }
            }
        }

        return new Computation(errors, warnings, requested, repayment, tenor, start,
                totalInterest, total, minRequired, installments, finalInstallment, endMonth, lines);
    }

    private BigDecimal interestFor(LoanInterestType type, BigDecimal rate, BigDecimal principal, int tenor) {
        if (type != LoanInterestType.INTEREST_BEARING || rate == null || rate.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return principal.multiply(rate)
                .multiply(BigDecimal.valueOf(tenor))
                .divide(PERCENT_MONTHS, 2, ROUNDING);
    }

    private LoanRepaymentPreviewResponse toResponse(Computation c,
                                                    Long productId,
                                                    String productName,
                                                    LoanInterestType interestType,
                                                    BigDecimal interestRate) {
        return LoanRepaymentPreviewResponse.builder()
                .valid(c.errors().isEmpty())
                .errors(c.errors())
                .warnings(c.warnings())
                .loanProductId(productId)
                .loanProductName(productName)
                .interestType(interestType)
                .interestRate(interestRate)
                .requestedAmount(c.requested())
                .repaymentAmount(c.repayment())
                .tenorMonths(c.tenor())
                .repaymentStartMonth(c.start())
                .totalInterestAmount(c.totalInterest())
                .totalRepayableAmount(c.total())
                .numberOfInstallments(c.installments())
                .finalInstallmentAmount(c.finalInstallment())
                .expectedEndMonth(c.endMonth())
                .minimumRequiredRepaymentAmount(c.minRequired())
                .lines(c.lines())
                .build();
    }

    // ------------------------------------------------------------------ helpers

    private boolean checkMoney(BigDecimal value, String label, List<String> errors) {
        if (value == null) {
            errors.add(label + " is required.");
            return false;
        }
        if (value.signum() <= 0) {
            errors.add(label + " must be greater than zero.");
            return false;
        }
        if (value.stripTrailingZeros().scale() > 2) {
            errors.add(label + " cannot have more than two decimal places.");
            return false;
        }
        return true;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, ROUNDING);
    }

    private static String fmt(BigDecimal value) {
        return value.setScale(2, ROUNDING).toPlainString();
    }

    private record Computation(List<String> errors,
                               List<String> warnings,
                               BigDecimal requested,
                               BigDecimal repayment,
                               Integer tenor,
                               LocalDate start,
                               BigDecimal totalInterest,
                               BigDecimal total,
                               BigDecimal minRequired,
                               Integer installments,
                               BigDecimal finalInstallment,
                               LocalDate endMonth,
                               List<LoanRepaymentScheduleLineResponse> lines) {
    }
}
