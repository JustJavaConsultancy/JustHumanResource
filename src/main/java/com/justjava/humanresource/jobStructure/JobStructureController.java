package com.justjava.humanresource.jobStructure;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class JobStructureController {
    @GetMapping("/job-structure")
    public String getJobStructure(Model model) {
        model.addAttribute("title","Job Structure Management");
        model.addAttribute("subTitle","Define and manage job roles, hierarchies, and reporting structures");
        return "jobStructure/main";
    }
}
