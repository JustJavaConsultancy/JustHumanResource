package com.justjava.humanresource.promotion;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@PreAuthorize("@promotionSecurity.canAccessPromotionWorkArea()")
public class PromotionController {
    @GetMapping("/promotion")
    public String getPromotions(Model model) {
        return promotionPage(model, "dashboard");
    }

    @GetMapping("/promotion/requests")
    public String requests(Model model) {
        return promotionPage(model, "requests");
    }

    @GetMapping("/promotion/approvals")
    public String approvals(Model model) {
        return promotionPage(model, "approvals");
    }

    @GetMapping("/promotion/reports")
    public String reports(Model model) {
        return promotionPage(model, "reports");
    }

    @GetMapping("/promotion/configuration")
    public String configuration(Model model) {
        return promotionPage(model, "configuration");
    }

    @GetMapping("/promotion/payroll-handoffs")
    public String payrollHandoffs(Model model) {
        return promotionPage(model, "payrollHandoffs");
    }

    private String promotionPage(Model model, String initialSection) {
        model.addAttribute("title", "Promotion Management");
        model.addAttribute("subTitle", "Manage employee promotions, approvals, and career progression");
        model.addAttribute("initialSection", initialSection);
        return "promotion/main";
    }
}
