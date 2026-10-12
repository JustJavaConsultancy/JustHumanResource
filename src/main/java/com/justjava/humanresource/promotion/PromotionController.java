package com.justjava.humanresource.promotion;


import com.justjava.humanresource.promotion.service.PromotionSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PromotionController {
    private final PromotionSecurity promotionSecurity;

    @GetMapping("/promotion")
    @PreAuthorize("@promotionSecurity.canAccessPromotionWorkArea()")
    public String getPromotions(Model model) {
        return legacyRedirect("dashboard");
    }

    @GetMapping("/promotion/requests")
    @PreAuthorize("@promotionSecurity.canAccessPromotionWorkArea()")
    public String requests(Model model) {
        return legacyRedirect("requests");
    }

    @GetMapping("/promotion/approvals")
    @PreAuthorize("@promotionSecurity.canAccessPromotionWorkArea()")
    public String approvals(Model model) {
        return legacyRedirect("approvals");
    }

    @GetMapping("/promotion/reports")
    @PreAuthorize("@promotionSecurity.canAccessPromotionWorkArea()")
    public String reports(Model model) {
        return legacyRedirect("reports");
    }

    @GetMapping("/promotion/configuration")
    @PreAuthorize("@promotionSecurity.canAccessPromotionWorkArea()")
    public String configuration(Model model) {
        return legacyRedirect("configuration");
    }

    @GetMapping("/promotion/payroll-handoffs")
    @PreAuthorize("@promotionSecurity.canAccessPromotionWorkArea()")
    public String payrollHandoffs(Model model) {
        return legacyRedirect("payrollHandoffs");
    }

    @GetMapping("/admin/promotions")
    @PreAuthorize("@promotionSecurity.canAccessAdminPromotionPage()")
    public String adminPromotions(@RequestParam(required = false) String section, Model model) {
        return promotionPage(model, section, "admin", "layout", "promotion/manage");
    }

    @GetMapping("/hr/promotions")
    @PreAuthorize("@promotionSecurity.canAccessHrPromotionPage()")
    public String hrPromotions(@RequestParam(required = false) String section, Model model) {
        return promotionPage(model, section, "hr", "layout", "promotion/manage");
    }

    @GetMapping("/finance/promotions")
    @PreAuthorize("@promotionSecurity.canAccessFinancePromotionPage()")
    public String financePromotions(@RequestParam(required = false) String section, Model model) {
        return promotionPage(model, section, "finance", "layouts/financialOfficerLayout", "promotion/finance");
    }

    @GetMapping("/executive/promotions")
    @PreAuthorize("@promotionSecurity.canAccessExecutivePromotionPage()")
    public String executivePromotions(@RequestParam(required = false) String section, Model model) {
        return promotionPage(model, section, "executive", "layout", "promotion/executive");
    }

    @GetMapping("/manager/promotions")
    @PreAuthorize("@promotionSecurity.canAccessManagerPromotionPage()")
    public String managerPromotions(@RequestParam(required = false) String section, Model model) {
        return promotionPage(model, section, "manager", "layout", "promotion/manager");
    }

    @GetMapping("/employee/promotions")
    @PreAuthorize("@promotionSecurity.canAccessEmployeePromotionPage()")
    public String employeePromotions(@RequestParam(required = false) String section, Model model) {
        return promotionPage(model, section, "employee", "layouts/employeeLayout", "promotion/employee");
    }

    private String promotionPage(Model model, String requestedSection, String role, String layout, String view) {
        model.addAttribute("title", "Promotion Management");
        model.addAttribute("subTitle", "Manage employee promotions, approvals, and career progression");
        model.addAttribute("initialSection", allowedSection(requestedSection, role));
        model.addAttribute("promotionRole", role);
        model.addAttribute("promotionLayout", layout);
        model.addAttribute("canUsePromotionDashboard", isOneOf(role, "admin", "hr", "finance"));
        model.addAttribute("canUsePromotionRequests", isOneOf(role, "admin", "hr", "finance", "executive", "manager", "employee"));
        model.addAttribute("canUsePromotionApprovals", isOneOf(role, "admin", "hr", "finance", "executive", "manager"));
        model.addAttribute("canUsePromotionReports", isOneOf(role, "admin", "hr", "finance"));
        model.addAttribute("canUsePromotionConfiguration", isOneOf(role, "admin", "hr"));
        model.addAttribute("canUsePromotionPayrollHandoffs", isOneOf(role, "admin", "hr", "finance"));
        model.addAttribute("canManagePromotionRequests", isOneOf(role, "admin", "hr"));
        return view;
    }

    private String legacyRedirect(String section) {
        if (promotionSecurity.canAccessAdminPromotionPage()) return "redirect:/admin/promotions?section=" + section;
        if (promotionSecurity.canAccessHrPromotionPage()) return "redirect:/hr/promotions?section=" + section;
        if (promotionSecurity.canAccessFinancePromotionPage()) return "redirect:/finance/promotions?section=" + section;
        if (promotionSecurity.canAccessExecutivePromotionPage()) return "redirect:/executive/promotions?section=" + section;
        if (promotionSecurity.canAccessManagerPromotionPage()) return "redirect:/manager/promotions?section=" + section;
        if (promotionSecurity.canAccessEmployeePromotionPage()) return "redirect:/employee/promotions?section=" + section;
        return "redirect:/";
    }

    private String allowedSection(String requestedSection, String role) {
        String section = requestedSection == null || requestedSection.isBlank() ? defaultSection(role) : requestedSection;
        return switch (role) {
            case "admin", "hr" -> isOneOf(section, "dashboard", "requests", "approvals", "reports", "configuration", "payrollHandoffs")
                    ? section : "dashboard";
            case "finance" -> isOneOf(section, "dashboard", "requests", "approvals", "reports", "payrollHandoffs")
                    ? section : "approvals";
            case "executive", "manager" -> isOneOf(section, "approvals", "requests")
                    ? section : "approvals";
            case "employee" -> isOneOf(section, "requests")
                    ? section : "requests";
            default -> "dashboard";
        };
    }

    private String defaultSection(String role) {
        return switch (role) {
            case "finance", "executive", "manager" -> "approvals";
            case "employee" -> "requests";
            default -> "dashboard";
        };
    }

    private boolean isOneOf(String value, String... allowed) {
        for (String item : allowed) {
            if (item.equals(value)) return true;
        }
        return false;
    }
}
