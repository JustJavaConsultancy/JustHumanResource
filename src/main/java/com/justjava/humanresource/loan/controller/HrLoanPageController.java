package com.justjava.humanresource.loan.controller;

import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** HR/admin pages. Everyone else is sent to the page that fits their role. */
@Controller
@RequiredArgsConstructor
public class HrLoanPageController {

    private final AuthenticationManager authenticationManager;

    @GetMapping("/loans")
    public String loans(Model model) {
        String redirect = redirectIfNotHr();
        if (redirect != null) {
            return redirect;
        }
        model.addAttribute("title", "Employee Loans");
        model.addAttribute("subTitle", "Loan products, approvals, active loans, and missed deductions");
        model.addAttribute("initialTab", "applications");
        return "loan/hr-main";
    }

    /** Same page, opened on the loan product setup tab. */
    @GetMapping("/loans/products")
    public String products(Model model) {
        String redirect = redirectIfNotHr();
        if (redirect != null) {
            return redirect;
        }
        model.addAttribute("title", "Loan Products");
        model.addAttribute("subTitle", "Set up loan products, terms, and approval routes");
        model.addAttribute("initialTab", "products");
        return "loan/hr-main";
    }

    @GetMapping("/loans/{id:\\d+}")
    public String loanDetail(Model model) {
        String redirect = redirectIfNotHr();
        if (redirect != null) {
            return redirect;
        }
        model.addAttribute("title", "Loan Detail");
        model.addAttribute("subTitle", "Review terms, employee context, approvals, and repayment schedule");
        return "loan/hr-detail";
    }

    private String redirectIfNotHr() {
        if (authenticationManager.isHumanResource() || authenticationManager.isAdmin()) {
            return null;
        }
        return authenticationManager.isFinancialOfficer() ? "redirect:/finance/loans" : "redirect:/employee/loans";
    }
}
