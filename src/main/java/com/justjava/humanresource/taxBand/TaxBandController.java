package com.justjava.humanresource.taxBand;

import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;

@Controller
public class TaxBandController {
    @Autowired
    private PayrollSetupService payrollSetupService;

    @GetMapping("/tax-band")
    public String taxBand(Model model){

        LocalDate date = LocalDate.now();

        System.out.println("Active bands " + payrollSetupService.getActivePayeBands(date));

        model.addAttribute("title", "Tax Band");
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
