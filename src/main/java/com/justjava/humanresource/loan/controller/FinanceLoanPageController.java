package com.justjava.humanresource.loan.controller;

import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Finance/admin pages. Everyone else is sent to the page that fits their role. */
@Controller
@RequiredArgsConstructor
public class FinanceLoanPageController {

    private final AuthenticationManager authenticationManager;

    @GetMapping("/finance/loans")
    public String loans(Model model) {
        String redirect = redirectIfNotFinance();
        if (redirect != null) {
            return redirect;
        }
        model.addAttribute("title", "Employee Loans");
        model.addAttribute("subTitle", "Finance approvals, active loans, and payroll deduction impact");
        return "loan/finance-main";
    }

    @GetMapping("/finance/loans/{id:\\d+}")
    public String loanDetail(Model model) {
        String redirect = redirectIfNotFinance();
        if (redirect != null) {
            return redirect;
        }
        model.addAttribute("title", "Loan Detail");
        model.addAttribute("subTitle", "Review terms, approvals, repayment schedule, and payroll impact");
        return "loan/finance-detail";
    }

    private String redirectIfNotFinance() {
        if (authenticationManager.isFinancialOfficer() || authenticationManager.isAdmin()) {
            return null;
        }
        return authenticationManager.isHumanResource() ? "redirect:/loans" : "redirect:/employee/loans";
    }
}
