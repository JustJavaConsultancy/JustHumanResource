package com.justjava.humanresource.pension;

import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Controller
public class PensionController {
    @Autowired
    PayrollSetupService payrollSetupService;

    @GetMapping("/pension")
    public String getPension(Model model) {
        List<PensionScheme> schemes = payrollSetupService.getActivePensionSchemes();
        int total = schemes.size();

        BigDecimal avgEmployeeRate = BigDecimal.ZERO, avgEmployerRate = BigDecimal.ZERO;
        BigDecimal minEmployeeRate = BigDecimal.ZERO, maxEmployeeRate = BigDecimal.ZERO;
        long schemesWithCap = 0;
        BigDecimal avgCap = null;
        LocalDate nextEndDate = null;

        if (total > 0) {
            avgEmployeeRate = schemes.stream().map(PensionScheme::getEmployeeRate)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

            avgEmployerRate = schemes.stream().map(PensionScheme::getEmployerRate)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

            minEmployeeRate = schemes.stream().map(PensionScheme::getEmployeeRate)
                    .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            maxEmployeeRate = schemes.stream().map(PensionScheme::getEmployeeRate)
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

            schemesWithCap = schemes.stream()
                    .filter(s -> s.getPensionableCap() != null).count();

            if (schemesWithCap > 0) {
                BigDecimal capSum = schemes.stream()
                        .filter(s -> s.getPensionableCap() != null)
                        .map(PensionScheme::getPensionableCap)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                avgCap = capSum.divide(BigDecimal.valueOf(schemesWithCap), 2, RoundingMode.HALF_UP);
            }

            nextEndDate = schemes.stream()
                    .filter(s -> s.getEffectiveTo() != null && s.getEffectiveTo().isAfter(LocalDate.now()))
                    .map(PensionScheme::getEffectiveTo)
                    .min(LocalDate::compareTo).orElse(null);
        }

        model.addAttribute("schemes",         schemes);
        model.addAttribute("avgEmployeeRate",  avgEmployeeRate);
        model.addAttribute("avgEmployerRate",  avgEmployerRate);
        model.addAttribute("totalAvgRate",     avgEmployeeRate.add(avgEmployerRate));
        model.addAttribute("minEmployeeRate",  minEmployeeRate);
        model.addAttribute("maxEmployeeRate",  maxEmployeeRate);
        model.addAttribute("schemesWithCap",   schemesWithCap);
        model.addAttribute("avgCap",           avgCap);          // null when none
        model.addAttribute("nextEndDate",      nextEndDate);      // null when none
        model.addAttribute("title",   "Pension Management");
        model.addAttribute("subTitle","Manage employee pension details and contributions");
        return "pension/main";
    }
    @PostMapping("/setup/pension-scheme")
    public String createPensionScheme(PensionScheme scheme) {
        payrollSetupService.createPensionScheme(scheme);
        return "redirect:/pension";
    }
}
