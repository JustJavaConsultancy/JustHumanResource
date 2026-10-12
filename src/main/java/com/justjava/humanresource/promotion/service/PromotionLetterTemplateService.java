package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PromotionLetterTemplateService {

    private static final String TEMPLATE_VERSION = "v1";
    private static final Map<PromotionAttachmentType, TemplateDefinition> TEMPLATES = Map.of(
            PromotionAttachmentType.PROMOTION_LETTER,
            new TemplateDefinition("promotion-letter-standard", "Standard Promotion Letter", TEMPLATE_VERSION),
            PromotionAttachmentType.SALARY_ADJUSTMENT_LETTER,
            new TemplateDefinition("salary-adjustment-letter-standard", "Standard Salary Adjustment Letter", TEMPLATE_VERSION)
    );

    public RenderedTemplate render(PromotionRequest request, PromotionAttachmentType documentType) {
        TemplateDefinition template = template(documentType);
        List<String> lines = documentType == PromotionAttachmentType.PROMOTION_LETTER
                ? promotionLetter(request)
                : salaryAdjustmentLetter(request);
        return new RenderedTemplate(template.getKey(), template.getName(), template.getVersion(), lines);
    }

    public TemplateDefinition template(PromotionAttachmentType documentType) {
        TemplateDefinition template = TEMPLATES.get(documentType);
        if (template == null) {
            throw new IllegalArgumentException("Only promotion and salary adjustment letters can be generated.");
        }
        return template;
    }

    private List<String> promotionLetter(PromotionRequest request) {
        return List.of(
                "Promotion Letter",
                "",
                "Template: " + template(PromotionAttachmentType.PROMOTION_LETTER).getName(),
                "Employee: " + employeeName(request),
                "Request Number: " + nullSafeText(request.getRequestNumber()),
                "Current Role: " + (request.getCurrentRole() == null ? "N/A" : nullSafeText(request.getCurrentRole().getName())),
                "Proposed Role: " + (request.getProposedRole() == null ? "N/A" : nullSafeText(request.getProposedRole().getName())),
                "Effective Date: " + (request.getEffectiveDate() == null ? "N/A" : request.getEffectiveDate().format(DateTimeFormatter.ISO_DATE)),
                "",
                "This letter confirms the approved promotion recorded in the HR promotion request."
        );
    }

    private List<String> salaryAdjustmentLetter(PromotionRequest request) {
        return List.of(
                "Salary Adjustment Letter",
                "",
                "Template: " + template(PromotionAttachmentType.SALARY_ADJUSTMENT_LETTER).getName(),
                "Employee: " + employeeName(request),
                "Request Number: " + nullSafeText(request.getRequestNumber()),
                "Effective Date: " + (request.getEffectiveDate() == null ? "N/A" : request.getEffectiveDate().format(DateTimeFormatter.ISO_DATE)),
                "Previous Salary: " + amountText(request.getCurrentSalary()),
                "New Salary: " + amountText(request.getProposedSalary()),
                "Monthly Payroll Impact: " + amountText(request.getMonthlyPayrollImpact()),
                "Annual Payroll Impact: " + amountText(request.getAnnualPayrollImpact()),
                "",
                "This letter confirms the salary adjustment associated with the approved promotion request."
        );
    }

    private String employeeName(PromotionRequest request) {
        return request.getEmployee() == null ? "N/A" : nullSafeText(request.getEmployee().getFullName());
    }

    private String nullSafeText(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String amountText(Object value) {
        return value == null ? "0.00" : value.toString();
    }

    @Value
    public static class TemplateDefinition {
        String key;
        String name;
        String version;
    }

    @Value
    public static class RenderedTemplate {
        String key;
        String name;
        String version;
        List<String> lines;
    }
}
