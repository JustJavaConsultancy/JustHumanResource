package com.justjava.humanresource.leave;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LeaveController {
    @GetMapping("/leave")
    public String leavePage(Model model) {
        model.addAttribute("title","Leave Management");
        model.addAttribute("subTitle","Approve and manage employee leave requests");
        return "leave/main";
    }
}
