package com.justjava.humanresource;

import com.justjava.humanresource.aau.AuthenticationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {
    @Autowired
    AuthenticationManager authenticationManager;

    @GetMapping("/")
    public String home(Model model) {
        System.out.println(authenticationManager.getAllAttributes()); // hypothetical method
        System.out.println(" Is Employee ==="+authenticationManager.isEmployee());
        if (authenticationManager.isEmployee()){
            return "redirect:/employee/dashboard";
        }

        model.addAttribute("title", "Welcome to JustJava HR");
        model.addAttribute("subTitle", "Streamline your HR processes with ease");
        return "index";
    }

}
