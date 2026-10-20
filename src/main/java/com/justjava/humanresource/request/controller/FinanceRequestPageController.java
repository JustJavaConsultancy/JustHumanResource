package com.justjava.humanresource.request.controller;

import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Finance-only browser pages for the organizational request workflow (Step 2: read-only).
 * Access is enforced server-side; hiding the sidebar link is not a security control.
 */
@Controller
@RequiredArgsConstructor
public class FinanceRequestPageController {

    private final AuthenticationManager auth;

    @GetMapping("/finance/requests")
    public String requests(Model model) {
        requireFinance();
        model.addAttribute("title", "Requests");
        model.addAttribute("subTitle", "Review requests submitted across the organization");
        return "finance/requests";
    }

    @GetMapping("/finance/requests/{id}")
    public String detail(@PathVariable Long id, Model model) {
        requireFinance();
        model.addAttribute("requestId", id);
        model.addAttribute("title", "Request Detail");
        model.addAttribute("subTitle", "Review request details, approvals, comments, and attachments");
        return "finance/request-detail";
    }

    private void requireFinance() {
        if (!auth.isFinancialOfficer()) {
            throw new AccessDeniedException("Financial officer access is required.");
        }
    }
}