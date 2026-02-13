package com.justjava.humanresource.pension;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PensionController {
    @GetMapping("/pension")
    public String getPension(Model model){
        model.addAttribute("title", "Pension Management");
        model.addAttribute("subTitle", "Manage employee pension details and contributions");
        return "pension/main";
    }
}
