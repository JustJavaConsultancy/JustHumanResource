package com.justjava.humanresource.promotion;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PromotionController {
    @GetMapping("/promotion")
    public String getPromotions(Model model) {
        model.addAttribute("title","Promotion Management");
        model.addAttribute("subTitle","Manage employee promotions, approvals, and career progression");
        return "promotion/main";
    }
}
