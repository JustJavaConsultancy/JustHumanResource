package com.justjava.humanresource.request.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkflowRequestPageController {

    @GetMapping("/requests")
    public String requests(Model model) {
        model.addAttribute("title", "Requests");
        model.addAttribute("subTitle", "Create, approve, and track organizational requests");
        return "request/main";
    }

    @GetMapping("/requests/{id}")
    public String detail(Model model) {
        model.addAttribute("title", "Request Detail");
        model.addAttribute("subTitle", "Review request details, approvals, comments, and attachments");
        return "request/detail";
    }

    @GetMapping("/requests/userGuide")
    public String userGuide(Model model) {
        model.addAttribute("title", "Request Workflow User Guide");
        model.addAttribute("subTitle", "How to create, submit, approve, and track organizational requests");
        return "request/userGuide";
    }
}
