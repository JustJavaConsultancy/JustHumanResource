package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionEmailNotificationChannel implements PromotionNotificationChannel {

    private final EmailService emailService;

    @Override
    public String name() {
        return "email";
    }

    @Override
    public void send(Employee recipient, String subject, String htmlBody, String textBody, String context) {
        if (recipient == null) {
            log.warn("Promotion email: skipping {} - no recipient.", context);
            return;
        }
        String email = recipient.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Promotion email: skipping {} for employee {} - no email address on file.",
                    context, recipient.getId());
            return;
        }
        try {
            emailService.sendEmail(email.trim(), subject, htmlBody, textBody);
        } catch (Exception e) {
            log.warn("Promotion email: failed to send {} to employee {}: {}",
                    context, recipient.getId(), e.getMessage());
        }
    }
}
