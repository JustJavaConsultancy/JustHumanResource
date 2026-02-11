package com.justjava.humanresource;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Welcome to JustJava HR");
        model.addAttribute("subTitle", "Streamline your HR processes with ease");
        return "index";
    }

}
