package com.justjava.humanresource.finance;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FinanceController {
    @GetMapping("/finance")
    public String getFinancePage(Model model) {
        model.addAttribute("title","Finance Management");
        model.addAttribute("subTitle","Manage financial operations, budgeting, and reporting");
        return "finance/finance";
    }
}
