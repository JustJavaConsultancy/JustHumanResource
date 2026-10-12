package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.entity.PromotionApprovalStep;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionEmailService {

    private static final String SIGNATURE = "Human Resources";

    private final EmployeeRepository employeeRepository;
    private final List<PromotionNotificationChannel> channels;

    public void sendSubmitted(PromotionRequest request) {
        Employee employee = employee(request);
        sendTo(employee, "Promotion request submitted",
                "Your promotion request " + requestNumber(request) + " has been submitted for approval.",
                "promotion submission notice");
    }

    public void sendApprovalAssigned(PromotionRequest request, PromotionApprovalStep step) {
        if (step == null) {
            return;
        }
        sendToApprovalRecipient(request, step, "Promotion approval pending",
                "Promotion request " + requestNumber(request) + " is awaiting your " + step.getStage() + " review.",
                "promotion approval assignment notice");
    }

    public void sendApprovalReminder(PromotionRequest request, PromotionApprovalStep step) {
        if (step == null) {
            return;
        }
        sendToApprovalRecipient(request, step, "Promotion approval reminder",
                "Promotion request " + requestNumber(request) + " is still awaiting your " + step.getStage() + " review.",
                "promotion approval reminder notice");
    }

    public void sendApprovalEscalated(PromotionRequest request, PromotionApprovalStep step) {
        if (step == null) {
            return;
        }
        sendToApprovalRecipient(request, step, "Promotion approval escalated",
                "Promotion request " + requestNumber(request) + " has been escalated to you for " + step.getStage() + " review.",
                "promotion approval escalation notice");
    }

    public void sendApproved(PromotionRequest request) {
        sendTo(employee(request), "Promotion request approved",
                "Your promotion request " + requestNumber(request) + " has been approved.",
                "promotion approval notice");
    }

    public void sendRejected(PromotionRequest request) {
        sendTo(employee(request), "Promotion request rejected",
                "Your promotion request " + requestNumber(request) + " has been rejected.",
                "promotion rejection notice");
    }

    public void sendReturned(PromotionRequest request) {
        sendTo(employee(request), "Promotion request returned",
                "Your promotion request " + requestNumber(request)
                        + " has been returned for correction. Please review the comments and resubmit when ready.",
                "promotion return notice");
    }

    public void sendCancelled(PromotionRequest request) {
        sendTo(employee(request), "Promotion request cancelled",
                "Promotion request " + requestNumber(request) + " has been cancelled.",
                "promotion cancellation notice");
    }

    public void sendImplemented(PromotionRequest request) {
        sendTo(employee(request), "Promotion implemented",
                "Your approved promotion request " + requestNumber(request) + " has been implemented.",
                "promotion implementation notice");
    }

    private void sendToApprovalRecipient(PromotionRequest request, PromotionApprovalStep step,
                                         String subject, String message, String context) {
        if (step.getApprover() != null) {
            sendTo(step.getApprover(), subject, message, context);
            return;
        }
        String group = step.getApproverGroup();
        if (group == null || group.isBlank()) {
            log.warn("Promotion notification: skipping {} for request {} - no approver or group.",
                    context, requestNumber(request));
            return;
        }
        List<Employee> recipients = groupRecipients(group);
        if (recipients.isEmpty()) {
            log.warn("Promotion notification: skipping {} for group {} - no active recipients.",
                    context, group);
            return;
        }
        recipients.forEach(recipient -> sendTo(recipient, subject, message, context));
    }

    private List<Employee> groupRecipients(String group) {
        String normalized = group.trim().toLowerCase(Locale.ROOT);
        List<Employee> employees = employeeRepository.findActiveEmployeesInAnyGroupIgnoreCase(
                List.of(normalized, "/" + normalized));
        Map<Long, Employee> unique = new LinkedHashMap<>();
        for (Employee employee : employees) {
            if (employee.getId() != null) {
                unique.put(employee.getId(), employee);
            }
        }
        return List.copyOf(unique.values());
    }

    private void sendTo(Employee recipient, String subject, String message, String context) {
        if (recipient == null) {
            log.warn("Promotion notification: skipping {} - no recipient.", context);
            return;
        }
        String greetingName = recipient.getFullName() == null || recipient.getFullName().isBlank()
                ? "Colleague"
                : recipient.getFullName();
        String text = "Dear " + greetingName + ",\n\n"
                + message + "\n\n"
                + "Regards,\n" + SIGNATURE;
        String html = "<p>Dear " + html(greetingName) + ",</p>"
                + "<p>" + html(message) + "</p>"
                + "<p>Regards,<br><strong>" + SIGNATURE + "</strong></p>";
        channels.stream()
                .filter(PromotionNotificationChannel::enabled)
                .forEach(channel -> channel.send(recipient, subject, html, text, context));
    }

    private Employee employee(PromotionRequest request) {
        return request == null ? null : request.getEmployee();
    }

    private String requestNumber(PromotionRequest request) {
        if (request == null || request.getRequestNumber() == null || request.getRequestNumber().isBlank()) {
            return "the promotion request";
        }
        return request.getRequestNumber();
    }

    private String html(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
