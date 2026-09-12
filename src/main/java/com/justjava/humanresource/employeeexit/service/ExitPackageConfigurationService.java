package com.justjava.humanresource.employeeexit.service;

import com.justjava.humanresource.employeeexit.dto.ExitPackageComponentCommand;
import com.justjava.humanresource.employeeexit.dto.ExitPackageRuleCommand;
import com.justjava.humanresource.employeeexit.entity.ExitPackageComponent;
import com.justjava.humanresource.employeeexit.entity.ExitPackageRule;
import com.justjava.humanresource.employeeexit.repository.ExitPackageComponentRepository;
import com.justjava.humanresource.employeeexit.repository.ExitPackageRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExitPackageConfigurationService {
    private final ExitPackageComponentRepository components;
    private final ExitPackageRuleRepository rules;

    @Transactional
    public ExitPackageComponent saveComponent(ExitPackageComponentCommand command) {
        ExitPackageComponent component = components.findByComponentCode(command.getComponentCode())
                .orElseGet(ExitPackageComponent::new);
        component.setComponentCode(command.getComponentCode().trim().toUpperCase());
        component.setName(command.getName().trim());
        component.setLineType(command.getLineType());
        component.setEarning(command.isEarning());
        component.setTaxable(command.isTaxable());
        component.setPensionable(command.isPensionable());
        component.setActive(command.isActive());
        return components.save(component);
    }

    @Transactional
    public ExitPackageRule saveRule(ExitPackageRuleCommand command) {
        if (!components.existsById(command.getComponentId())) {
            throw new IllegalArgumentException("Exit package component not found.");
        }
        ExitPackageRule rule = new ExitPackageRule();
        rule.setComponentId(command.getComponentId());
        rule.setCalculationMethod(command.getCalculationMethod());
        rule.setFixedAmount(command.getFixedAmount());
        rule.setPercentage(command.getPercentage());
        rule.setAppliesToExitTypes(command.getAppliesToExitTypes());
        rule.setMinimumYearsOfService(command.getMinimumYearsOfService());
        rule.setMaximumYearsOfService(command.getMaximumYearsOfService());
        rule.setRequiresManualApproval(command.isRequiresManualApproval());
        rule.setActive(command.isActive());
        return rules.save(rule);
    }
}
