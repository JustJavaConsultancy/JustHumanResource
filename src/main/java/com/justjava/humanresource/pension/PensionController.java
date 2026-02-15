package com.justjava.humanresource.pension;

import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class PensionController {
    @Autowired
    PayrollSetupService payrollSetupService;

    @GetMapping("/pension")
    public String getPension(Model model){
        List<PensionScheme> schemes = payrollSetupService.getActivePensionSchemes();
        System.out.println("Active Pension Schemes: " + schemes.size());
        model.addAttribute("schemes", schemes);
        model.addAttribute("title", "Pension Management");
        model.addAttribute("subTitle", "Manage employee pension details and contributions");
        return "pension/main";
    }
    @PostMapping("/setup/pension-scheme")
    public String createPensionScheme(PensionScheme scheme) {
        payrollSetupService.createPensionScheme(scheme);
        return "redirect:/pension";
    }
}
