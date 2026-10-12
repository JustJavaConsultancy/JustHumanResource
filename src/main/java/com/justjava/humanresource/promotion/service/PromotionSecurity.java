package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("promotionSecurity")
@RequiredArgsConstructor
public class PromotionSecurity {

    private final AuthenticationManager authenticationManager;

    public boolean canAccessPromotionWorkArea() {
        return authenticationManager.isAdmin()
                || authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer()
                || authenticationManager.isExecutive()
                || authenticationManager.isEmployee();
    }

    public boolean canManagePromotions() {
        return authenticationManager.isAdmin() || authenticationManager.isHumanResource();
    }

    public boolean canViewOperationalPromotions() {
        return authenticationManager.isAdmin()
                || authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer();
    }

    public boolean canViewPromotionSalaryData() {
        return authenticationManager.isAdmin()
                || authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer()
                || authenticationManager.isExecutive();
    }
}
