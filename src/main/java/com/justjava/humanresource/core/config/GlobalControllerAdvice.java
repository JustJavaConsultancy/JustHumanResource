package com.justjava.humanresource.core.config;


import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@Slf4j
@ControllerAdvice
public class GlobalControllerAdvice {
    @Autowired
    AuthenticationManager authenticationManager;

    @ModelAttribute("currentPath")
    public String getCurrentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
    @ModelAttribute("userName")
    public String addUserName(HttpServletRequest request) {
            return (String) authenticationManager.get("name");

    }
    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {

        log.error("Unhandled exception occurred", ex);
       System.out.println(" The Error is global====="+ ex.getMessage());

        model.addAttribute("errorMessage",
                ex.getMessage());

        return "error/500";
    }
}
