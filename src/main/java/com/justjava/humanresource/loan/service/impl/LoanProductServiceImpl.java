package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.approval.entity.CustomApprovalPath;
import com.justjava.humanresource.approval.repository.CustomApprovalPathRepository;
import com.justjava.humanresource.approval.repository.CustomApprovalPathStepRepository;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.loan.dto.LoanProductCommand;
import com.justjava.humanresource.loan.dto.LoanProductResponse;
import com.justjava.humanresource.loan.dto.LoanProductSummaryResponse;
import com.justjava.humanresource.loan.entity.LoanProduct;
import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanInterestType;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.LoanProductRepository;
import com.justjava.humanresource.loan.service.LoanProductService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanProductServiceImpl implements LoanProductService {

    private final LoanProductRepository productRepository;
    private final EmployeeLoanApplicationRepository applicationRepository;
    private final CustomApprovalPathRepository customApprovalPathRepository;
    private final CustomApprovalPathStepRepository customApprovalPathStepRepository;
    private final AuthenticationManager authenticationManager;

    // ------------------------------------------------------------------ commands

    @Override
    @Transactional
    public LoanProductResponse create(LoanProductCommand command) {
        requireManageAccess();
        validateBasics(command, null);
        validateApprovalRoute(command.getApprovalRouteType(), command.getCustomApprovalPathId());

        LoanProduct product = new LoanProduct();
        applyIdentity(product, command);
        applyTerms(product, command);
        product.setActive(true);
        product.setUsed(false);
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public LoanProductResponse update(Long id, LoanProductCommand command) {
        requireManageAccess();
        LoanProduct product = getEntity(id);
        validateBasics(command, id);

        boolean locked = isUsed(product);
        if (locked) {
            rejectLockedChanges(product, command);
        } else {
            validateApprovalRoute(command.getApprovalRouteType(), command.getCustomApprovalPathId());
        }

        applyIdentity(product, command);
        if (!locked) {
            applyTerms(product, command);
        }
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public LoanProductResponse deactivate(Long id) {
        requireManageAccess();
        LoanProduct product = getEntity(id);
        product.setActive(false);
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public LoanProductResponse reactivate(Long id) {
        requireManageAccess();
        LoanProduct product = getEntity(id);
        product.setActive(true);
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireManageAccess();
        LoanProduct product = getEntity(id);
        if (isUsed(product)) {
            throw new IllegalStateException(
                    "This loan product has been used by loan applications and cannot be deleted. Deactivate it instead.");
        }
        productRepository.delete(product);
    }

    @Override
    @Transactional
    public void markUsed(Long productId) {
        LoanProduct product = getEntity(productId);
        if (!product.isUsed()) {
            product.setUsed(true);
            productRepository.save(product);
        }
    }

    // ------------------------------------------------------------------ queries

    @Override
    public LoanProductResponse getById(Long id) {
        requireViewAccess();
        return toResponse(getEntity(id));
    }

    @Override
    public List<LoanProductResponse> listAll() {
        requireViewAccess();
        return productRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    public List<LoanProductSummaryResponse> listActive() {
        return productRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toSummary).toList();
    }

    @Override
    public LoanProduct getEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Loan product not found: " + id));
    }

    // ------------------------------------------------------------------ validation

    @Override
    public void validateApprovalRoute(LoanApprovalRouteType routeType, Long customApprovalPathId) {
        if (routeType == null) {
            throw new IllegalArgumentException("Approval route is required.");
        }
        if (routeType == LoanApprovalRouteType.ROLE_BASED) {
            return; // any customApprovalPathId is ignored and cleared on save
        }
        if (customApprovalPathId == null) {
            throw new IllegalArgumentException("Select a custom approval path for the custom approval route.");
        }
        CustomApprovalPath path = customApprovalPathRepository.findById(customApprovalPathId)
                .orElseThrow(() -> new IllegalArgumentException("The selected custom approval path does not exist."));
        if (!path.isEnabled()) {
            throw new IllegalArgumentException("The selected custom approval path \"" + path.getName() + "\" is disabled.");
        }
        if (customApprovalPathStepRepository
                .findByCustomApprovalPathIdOrderBySequenceNo(customApprovalPathId).isEmpty()) {
            throw new IllegalArgumentException("The selected custom approval path \"" + path.getName() + "\" has no approvers.");
        }
    }

    private void validateBasics(LoanProductCommand cmd, Long excludeId) {
        List<String> errors = new ArrayList<>();

        String code = trim(cmd.getCode());
        String name = trim(cmd.getName());
        if (code.isEmpty()) {
            errors.add("Product code is required.");
        } else {
            boolean codeTaken = excludeId == null
                    ? productRepository.existsByCodeIgnoreCase(code)
                    : productRepository.existsByCodeIgnoreCaseAndIdNot(code, excludeId);
            if (codeTaken) {
                errors.add("A loan product with code \"" + code + "\" already exists.");
            }
        }
        if (name.isEmpty()) {
            errors.add("Product name is required.");
        } else {
            boolean nameTaken = productRepository.findAllByOrderByNameAsc().stream()
                    .anyMatch(p -> p.getName().equalsIgnoreCase(name) && !Objects.equals(p.getId(), excludeId));
            if (nameTaken) {
                errors.add("A loan product named \"" + name + "\" already exists.");
            }
        }

        boolean amountsPresent = true;
        amountsPresent &= requirePositive(cmd.getMinimumAmount(), "Minimum amount", errors);
        amountsPresent &= requirePositive(cmd.getMaximumAmount(), "Maximum amount", errors);
        boolean repaymentPresent = requirePositive(cmd.getMinimumRepaymentAmount(), "Minimum repayment amount", errors);
        if (cmd.getMaximumTenorMonths() == null || cmd.getMaximumTenorMonths() < 1) {
            errors.add("Maximum tenor must be at least 1 month.");
        }
        if (amountsPresent && cmd.getMaximumAmount().compareTo(cmd.getMinimumAmount()) < 0) {
            errors.add("Maximum amount cannot be less than minimum amount.");
        }
        if (repaymentPresent && cmd.getMaximumAmount() != null
                && cmd.getMinimumRepaymentAmount().compareTo(cmd.getMaximumAmount()) > 0) {
            errors.add("Minimum repayment amount cannot be more than the maximum loan amount.");
        }

        if (cmd.getInterestType() == null) {
            errors.add("Interest type is required.");
        } else if (cmd.getInterestType() == LoanInterestType.INTEREST_BEARING
                && (cmd.getInterestRate() == null || cmd.getInterestRate().signum() <= 0)) {
            errors.add("Interest rate must be greater than zero for interest-bearing products.");
        }
        if (cmd.getRepaymentFrequency() == null) {
            errors.add("Repayment frequency is required.");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
    }

    private void rejectLockedChanges(LoanProduct p, LoanProductCommand cmd) {
        List<String> changed = new ArrayList<>();
        if (!sameAmount(p.getMinimumAmount(), cmd.getMinimumAmount())) changed.add("minimum amount");
        if (!sameAmount(p.getMaximumAmount(), cmd.getMaximumAmount())) changed.add("maximum amount");
        if (!sameAmount(p.getMinimumRepaymentAmount(), cmd.getMinimumRepaymentAmount())) changed.add("minimum repayment amount");
        if (!Objects.equals(p.getMaximumTenorMonths(), cmd.getMaximumTenorMonths())) changed.add("maximum tenor");
        if (p.getInterestType() != cmd.getInterestType()) changed.add("interest type");
        if (!sameAmount(p.getInterestRate(), normalizedRate(cmd))) changed.add("interest rate");
        if (p.getRepaymentFrequency() != cmd.getRepaymentFrequency()) changed.add("repayment frequency");
        if (p.getApprovalRouteType() != cmd.getApprovalRouteType()) changed.add("approval route");
        if (!Objects.equals(p.getCustomApprovalPathId(), normalizedPathId(cmd))) changed.add("custom approval path");

        if (!changed.isEmpty()) {
            throw new IllegalStateException("This loan product has already been used by loan applications, so these "
                    + "cannot be changed: " + String.join(", ", changed)
                    + ". Deactivate the product and create a new one instead.");
        }
    }

    // ------------------------------------------------------------------ mapping

    private void applyIdentity(LoanProduct p, LoanProductCommand cmd) {
        p.setCode(trim(cmd.getCode()));
        p.setName(trim(cmd.getName()));
        p.setDescription(cmd.getDescription() == null || cmd.getDescription().isBlank()
                ? null : cmd.getDescription().trim());
        p.setRequiresAttachment(cmd.isRequiresAttachment());
    }

    private void applyTerms(LoanProduct p, LoanProductCommand cmd) {
        p.setMinimumAmount(cmd.getMinimumAmount());
        p.setMaximumAmount(cmd.getMaximumAmount());
        p.setMinimumRepaymentAmount(cmd.getMinimumRepaymentAmount());
        p.setMaximumTenorMonths(cmd.getMaximumTenorMonths());
        p.setInterestType(cmd.getInterestType());
        p.setInterestRate(normalizedRate(cmd));
        p.setRepaymentFrequency(cmd.getRepaymentFrequency());
        p.setApprovalRouteType(cmd.getApprovalRouteType());
        p.setCustomApprovalPathId(normalizedPathId(cmd));
    }

    private LoanProductResponse toResponse(LoanProduct p) {
        long applicationCount = applicationRepository.countByLoanProductId(p.getId());
        boolean used = p.isUsed() || applicationCount > 0;
        return LoanProductResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .description(p.getDescription())
                .minimumAmount(p.getMinimumAmount())
                .maximumAmount(p.getMaximumAmount())
                .minimumRepaymentAmount(p.getMinimumRepaymentAmount())
                .maximumTenorMonths(p.getMaximumTenorMonths())
                .interestType(p.getInterestType())
                .interestRate(p.getInterestRate())
                .repaymentFrequency(p.getRepaymentFrequency())
                .approvalRouteType(p.getApprovalRouteType())
                .approvalRouteLabel(routeLabel(p.getApprovalRouteType()))
                .customApprovalPathId(p.getCustomApprovalPathId())
                .customApprovalPathName(pathName(p.getCustomApprovalPathId()))
                .requiresAttachment(p.isRequiresAttachment())
                .active(p.isActive())
                .used(used)
                .applicationCount(applicationCount)
                // activeLoanCount is wired in Step 9/13 once the loan account repository query is available
                .financialTermsEditable(!used)
                .deletable(!used)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private LoanProductSummaryResponse toSummary(LoanProduct p) {
        return LoanProductSummaryResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .description(p.getDescription())
                .minimumAmount(p.getMinimumAmount())
                .maximumAmount(p.getMaximumAmount())
                .minimumRepaymentAmount(p.getMinimumRepaymentAmount())
                .maximumTenorMonths(p.getMaximumTenorMonths())
                .interestType(p.getInterestType())
                .interestRate(p.getInterestRate())
                .approvalRouteType(p.getApprovalRouteType())
                .approvalRouteLabel(routeLabel(p.getApprovalRouteType()))
                .requiresAttachment(p.isRequiresAttachment())
                .active(p.isActive())
                .build();
    }

    // ------------------------------------------------------------------ helpers

    private void requireManageAccess() {
        if (!(authenticationManager.isHumanResource() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("Only HR or admin users can manage loan products.");
        }
    }

    private void requireViewAccess() {
        if (!(authenticationManager.isHumanResource()
                || authenticationManager.isAdmin()
                || authenticationManager.isFinancialOfficer())) {
            throw new AccessDeniedException("Only HR, finance or admin users can view all loan products.");
        }
    }

    private boolean isUsed(LoanProduct p) {
        return p.isUsed() || applicationRepository.existsByLoanProductId(p.getId());
    }

    private String pathName(Long pathId) {
        if (pathId == null) return null;
        return customApprovalPathRepository.findById(pathId).map(CustomApprovalPath::getName).orElse(null);
    }

    private static String routeLabel(LoanApprovalRouteType type) {
        return type == LoanApprovalRouteType.CUSTOM ? "Custom approval path" : "Role-based HR then Finance";
    }

    private static BigDecimal normalizedRate(LoanProductCommand cmd) {
        return cmd.getInterestType() == LoanInterestType.INTEREST_BEARING && cmd.getInterestRate() != null
                ? cmd.getInterestRate() : BigDecimal.ZERO;
    }

    private static Long normalizedPathId(LoanProductCommand cmd) {
        return cmd.getApprovalRouteType() == LoanApprovalRouteType.CUSTOM ? cmd.getCustomApprovalPathId() : null;
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    private static boolean requirePositive(BigDecimal value, String label, List<String> errors) {
        if (value == null || value.signum() <= 0) {
            errors.add(label + " must be greater than zero.");
            return false;
        }
        return true;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
