package com.justjava.humanresource.taxBand;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TaxBandController {
    @GetMapping("/tax-band")
    public String taxBand(Model model){
        model.addAttribute("title", "Tax Band");
        model.addAttribute("subTitle", "Manage tax bands for employees");
        return "taxBand/main";
    }
}
