package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("promotionSecurity")
@RequiredArgsConstructor
public class PromotionSecurity {

    private final AuthenticationManager authenticationManager;

    public boolean canAccessPromotionWorkArea() {
        return !isExcludedPromotionRole()
                && (authenticationManager.isAdmin()
                || authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer()
                || authenticationManager.isExecutive()
                || authenticationManager.isHiringManager()
                || authenticationManager.isDepartmentHead()
                || authenticationManager.isEmployee());
    }

    public boolean canAccessAdminPromotionPage() {
        return !isExcludedPromotionRole() && authenticationManager.isAdmin();
    }

    public boolean canAccessHrPromotionPage() {
        return !isExcludedPromotionRole() && authenticationManager.isHumanResource();
    }

    public boolean canAccessFinancePromotionPage() {
        return !isExcludedPromotionRole() && authenticationManager.isFinancialOfficer();
    }

    public boolean canAccessExecutivePromotionPage() {
        return !isExcludedPromotionRole() && authenticationManager.isExecutive();
    }

    public boolean canAccessManagerPromotionPage() {
        return !isExcludedPromotionRole()
                && (authenticationManager.isHiringManager() || authenticationManager.isDepartmentHead());
    }

    public boolean canAccessEmployeePromotionPage() {
        return !isExcludedPromotionRole() && authenticationManager.isEmployee();
    }

    public boolean canManagePromotions() {
        return !isExcludedPromotionRole()
                && (authenticationManager.isAdmin() || authenticationManager.isHumanResource());
    }

    public boolean canConfigurePromotions() {
        return canManagePromotions();
    }

    public boolean canImplementPromotions() {
        return canManagePromotions();
    }

    public boolean canViewOperationalPromotions() {
        return !isExcludedPromotionRole()
                && (authenticationManager.isAdmin()
                || authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer());
    }

    public boolean canManagePromotionPayrollHandoffs() {
        return !isExcludedPromotionRole()
                && (authenticationManager.isAdmin()
                || authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer());
    }

    public boolean canViewPromotionReferenceData() {
        return !isExcludedPromotionRole()
                && (authenticationManager.isAdmin()
                || authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer());
    }

    public boolean canViewPromotionSalaryData() {
        return !isExcludedPromotionRole()
                && (authenticationManager.isAdmin()
                || authenticationManager.isHumanResource()
                || authenticationManager.isFinancialOfficer()
                || authenticationManager.isExecutive());
    }

    private boolean isExcludedPromotionRole() {
        return authenticationManager.isJobHR()
                || authenticationManager.isRestrictedHr()
                || authenticationManager.isAssetManager()
                || authenticationManager.isAuditor();
    }
}
