package com.justjava.humanresource.taxBand;

import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class TaxBandController {
    @Autowired
    private PayrollSetupService payrollSetupService;

    @GetMapping("/tax-band")
    public String taxBand(Model model) {
        LocalDate today = LocalDate.now();
        List<PayeTaxBand> activeBands = payrollSetupService.getActivePayeBands(today);

        int total = activeBands.size();

        BigDecimal avgRate     = BigDecimal.ZERO;
        String primaryTaxYear  = String.valueOf(today.getYear());
        LocalDate earliestFrom = null;
        LocalDate nextExpiry   = null;
        long futureCount       = 0;

        if (total > 0) {
            avgRate = activeBands.stream()
                    .map(PayeTaxBand::getRate)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

            earliestFrom = activeBands.stream()
                    .map(PayeTaxBand::getEffectiveFrom)
                    .min(LocalDate::compareTo)
                    .orElse(null);

            if (earliestFrom != null) {
                primaryTaxYear = String.valueOf(earliestFrom.getYear());
            }

            nextExpiry = activeBands.stream()
                    .filter(b -> b.getEffectiveTo() != null
                            && b.getEffectiveTo().isAfter(today))
                    .map(PayeTaxBand::getEffectiveTo)
                    .min(LocalDate::compareTo)
                    .orElse(null);

            futureCount = activeBands.stream()
                    .filter(b -> b.getEffectiveFrom().isAfter(today))
                    .count();
        }

        // Regime summary: distinct regime codes → count per code
        Map<String, Long> regimeCounts = activeBands.stream()
                .filter(b -> b.getRegimeCode() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        PayeTaxBand::getRegimeCode,
                        java.util.stream.Collectors.counting()));

        model.addAttribute("activeBands",      activeBands);
        model.addAttribute("avgRate",          avgRate);
        model.addAttribute("primaryTaxYear",   primaryTaxYear);
        model.addAttribute("earliestFrom",     earliestFrom);
        model.addAttribute("nextExpiry",       nextExpiry);       // null = no upcoming expiry
        model.addAttribute("futureCount",      futureCount);
        model.addAttribute("regimeCounts",     regimeCounts);
        model.addAttribute("title",    "Tax Band");
        model.addAttribute("subTitle", "Manage tax bands for employees");
        return "taxBand/main";
    }

    @PostMapping("/addTaxBand")
    public String addTaxBand(PayeTaxBand band){
        // Logic to add a new tax band
        System.out.println("Adding Tax Band");
        payrollSetupService.createPayeTaxBand(band);
        System.out.println("Tax Band added successfully: " + band.getRegimeCode());
        return "redirect:/tax-band";
    }
}
