package com.justjava.humanresource.core.config;


import com.justjava.humanresource.payroll.service.diagnostics.PayrollJournalImbalanceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;

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

    @ModelAttribute("isRestrictedHr")
    public boolean isRestrictedHr() {
        return authenticationManager.isRestrictedHr();
    }

    @ModelAttribute("canAccessDocumentLibrary")
    public boolean canAccessDocumentLibrary() {
        return authenticationManager.isHumanResource() || authenticationManager.isAdmin();
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/500";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {

        log.error("Unhandled exception occurred", ex);
        System.out.println(" The Error is global====="+ ex.getMessage());

        model.addAttribute("errorMessage",
                ex.getMessage());
        PayrollJournalImbalanceException journalException = findCause(ex, PayrollJournalImbalanceException.class);
        if (journalException != null) {
            model.addAttribute("journalDiagnostics", journalException.getDiagnostics());
        }

        return "error/500";
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
