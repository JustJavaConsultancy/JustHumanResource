package com.justjava.humanresource.kpi;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class KpiController {
    @GetMapping("/kpi")
    public String attendancePage(Model model) {
        model.addAttribute("title","KPI Management");
        model.addAttribute("subTitle","Track and analyze employee performance metrics");
        return "kpi/main";
    }
}
