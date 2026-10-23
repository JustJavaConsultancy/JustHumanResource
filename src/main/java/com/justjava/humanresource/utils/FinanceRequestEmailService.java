package com.justjava.humanresource.utils;

import com.justjava.humanresource.aau.keycloak.KeycloakAdminService;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.orgStructure.entity.Company;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.entity.WorkflowRequestFinanceNotification;
import com.justjava.humanresource.request.enums.FinanceNotificationStatus;
import com.justjava.humanresource.request.repository.WorkflowRequestFinanceNotificationRepository;
import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@Slf4j
@Service
public class FinanceRequestEmailService {

    private static final String DEFAULT_COMPANY_NAME = "Human Resources";
    private static final String DEFAULT_OFFICER_NAME = "Finance Officer";
    private static final String FINANCE_REQUESTS_PATH = "/finance/requests";
    private static final int MAX_ERROR_LENGTH = 2000;

    private static final String BRAND = "#1e3a8a";
    private static final String TEXT = "#0f172a";
    private static final String MUTED = "#64748b";
    private static final String BORDER = "#e2e8f0";
    private static final String FONT = "'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private final EmailService emailService;
    private final KeycloakAdminService keycloakAdminService;
    private final WorkflowRequestFinanceNotificationRepository notificationRepository;
    private final WorkflowRequestRepository requestRepository;
    private final EmployeeService employeeService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    private final String realm;
    private final String financeGroupName;
    private final String appUrl;

    public FinanceRequestEmailService(
            EmailService emailService,
            KeycloakAdminService keycloakAdminService,
            WorkflowRequestFinanceNotificationRepository notificationRepository,
            WorkflowRequestRepository requestRepository,
            EmployeeService employeeService,
            AfterCommitExecutor afterCommitExecutor,
            PlatformTransactionManager transactionManager,
            @Value("${keycloak.base-realm}") String realm,
            @Value("${app.mail.finance-group:financialofficers}") String financeGroupName,
            @Value("${app.mail.app-url:${app.tracking.base-url:}}") String appUrl) {
        this.emailService = emailService;
        this.keycloakAdminService = keycloakAdminService;
        this.notificationRepository = notificationRepository;
        this.requestRepository = requestRepository;
        this.employeeService = employeeService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.realm = realm;
        this.financeGroupName = financeGroupName;
        this.appUrl = appUrl == null ? "" : appUrl.trim();
    }

    /* ============================================================
       ENQUEUE (inside the submit / send transaction)
       ============================================================ */

    /**
     * Records the Finance notification marker for this request and schedules delivery after commit.
     * Must be called from the transaction that performs the first submission, so the marker commits
     * (or rolls back) together with the request and its route.
     *
     * <p>If a marker already exists (resubmission, retry) nothing is enqueued. If two first-submit
     * transactions race, the unique constraint makes the second insert fail and that transaction
     * rolls back; the exception is deliberately NOT swallowed, so no second notification can exist
     * and unrelated database errors are never hidden.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueInitialSubmission(WorkflowRequest request) {
        if (request == null || request.getId() == null) {
            throw new IllegalArgumentException("A saved request is required to enqueue the Finance notification.");
        }
        Long requestId = request.getId();

        if (notificationRepository.existsByWorkflowRequestId(requestId)) {
            log.debug("Finance request email: marker already exists for request {} - not enqueuing again.", requestId);
            return;
        }

        WorkflowRequestFinanceNotification marker = new WorkflowRequestFinanceNotification();
        marker.setWorkflowRequestId(requestId);
        marker.setStatus(FinanceNotificationStatus.PENDING);
        marker = notificationRepository.saveAndFlush(marker);

        Long markerId = marker.getId();
        afterCommitExecutor.runAfterCommit(() -> dispatch(markerId));
    }

    /* ============================================================
       DISPATCH (after commit, async, no ambient transaction)
       ============================================================ */

    /** Delivers the notification identified by {@code notificationId}. Safe to call more than once. */
    public void dispatch(Long notificationId) {
        if (notificationId == null) {
            return;
        }
        try {
            Integer claimed = writeTx.execute(status -> notificationRepository.claimForSending(
                    notificationId, FinanceNotificationStatus.PENDING, FinanceNotificationStatus.SENDING,
                    LocalDateTime.now()));
            if (claimed == null || claimed == 0) {
                log.info("Finance request email: notification {} was not PENDING (already claimed or sent) - skipping.",
                        notificationId);
                return;
            }

            Long requestId = readTx.execute(status -> notificationRepository.findById(notificationId)
                    .map(WorkflowRequestFinanceNotification::getWorkflowRequestId).orElse(null));

            Map<String, UserRepresentation> officers = loadFinanceOfficers();
            if (officers.isEmpty()) {
                String msg = "No enabled users with an email address in group '" + financeGroupName
                        + "' (realm '" + realm + "').";
                log.warn("Finance request email: {} Notification {} (request {}) was NOT delivered.",
                        msg, notificationId, requestId);
                record(notificationId, FinanceNotificationStatus.FAILED, 0, 0, 0, msg);
                return;
            }

            String companyName = resolveCompanyName(requestId);
            String subject = "New request submitted";
            String link = financeRequestsUrl();
            if (link == null) {
                log.warn("Finance request email: no application URL configured (app.mail.app-url / app.tracking.base-url) - "
                        + "the email will be sent without a 'View requests' link.");
            }

            int sent = 0;
            List<String> failures = new ArrayList<>();
            for (Map.Entry<String, UserRepresentation> entry : officers.entrySet()) {
                String email = entry.getKey();
                String name = displayName(entry.getValue());
                try {
                    emailService.sendEmail(email, subject, buildHtml(name, companyName, link), buildText(name, companyName, link));
                    sent++;
                } catch (Exception e) {
                    log.warn("Finance request email: failed to send to {}: {}", email, e.getMessage());
                    failures.add(email + ": " + e.getMessage());
                }
            }

            int failed = failures.size();
            FinanceNotificationStatus finalStatus = sent > 0 ? FinanceNotificationStatus.SENT : FinanceNotificationStatus.FAILED;
            log.info("Finance request email: notification {} (request {}) sent to {} of {} finance officer(s).",
                    notificationId, requestId, sent, officers.size());
            record(notificationId, finalStatus, officers.size(), sent, failed,
                    failures.isEmpty() ? null : String.join("; ", failures));
        } catch (Exception e) {
            log.warn("Finance request email: dispatch of notification {} failed: {}", notificationId, e.getMessage(), e);
            try {
                record(notificationId, FinanceNotificationStatus.FAILED, 0, 0, 0, "Dispatch error: " + e.getMessage());
            } catch (Exception inner) {
                log.warn("Finance request email: could not record failure for notification {}: {}",
                        notificationId, inner.getMessage());
            }
        }
    }

    private void record(Long notificationId, FinanceNotificationStatus status,
                        int recipients, int sent, int failed, String error) {
        writeTx.executeWithoutResult(tx -> notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setStatus(status);
            n.setRecipientCount(recipients);
            n.setSentCount(sent);
            n.setFailedCount(failed);
            n.setLastError(truncate(error));
            if (sent > 0) {
                n.setSentAt(LocalDateTime.now());
            }
            notificationRepository.save(n);
        }));
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    /* ============================================================
       DATA
       ============================================================ */

    /** email (lower-case) -> user; de-duplicated, enabled users with an email only. */
    private Map<String, UserRepresentation> loadFinanceOfficers() {
        Map<String, UserRepresentation> result = new LinkedHashMap<>();
        List<UserRepresentation> members = keycloakAdminService.getUsersInGroup(realm, financeGroupName);
        if (members == null) {
            return result;
        }
        for (UserRepresentation user : members) {
            if (!Boolean.TRUE.equals(user.isEnabled())) {
                continue;
            }
            String email = user.getEmail();
            if (email == null || email.isBlank()) {
                log.warn("Finance request email: skipping user '{}' - no email address on file.", user.getUsername());
                continue;
            }
            result.putIfAbsent(email.trim().toLowerCase(Locale.ROOT), user);
        }
        return result;
    }

    /** Company branding only (sign-off); no request details are placed in the email. */
    private String resolveCompanyName(Long requestId) {
        if (requestId == null) {
            return DEFAULT_COMPANY_NAME;
        }
        try {
            String name = readTx.execute(status -> {
                WorkflowRequest request = requestRepository.findById(requestId).orElse(null);
                if (request == null) {
                    return null;
                }
                Employee requester = employeeService.getById(request.getRequesterEmployeeId());
                Company company = requester != null && requester.getDepartment() != null
                        ? requester.getDepartment().getCompany() : null;
                return company == null ? null : company.getName();
            });
            return name == null || name.isBlank() ? DEFAULT_COMPANY_NAME : name;
        } catch (Exception e) {
            log.warn("Finance request email: could not resolve company for request {}: {}", requestId, e.getMessage());
            return DEFAULT_COMPANY_NAME;
        }
    }

    private String financeRequestsUrl() {
        if (appUrl.isBlank()) {
            return null;
        }
        String base = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        return base + FINANCE_REQUESTS_PATH;
    }

    private String displayName(UserRepresentation user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        if (!full.isBlank()) {
            return full;
        }
        return user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername().trim() : DEFAULT_OFFICER_NAME;
    }

    /* ============================================================
       RENDERING
       ============================================================ */

    private String buildHtml(String officerName, String companyName, String link) {
        StringBuilder h = new StringBuilder(2500);
        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>New request submitted</title></head>")
                .append("<body style=\"margin:0;padding:0;background:#f1f5f9;font-family:").append(FONT).append(";\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:#f1f5f9;\">")
                .append("<tr><td align=\"center\" style=\"padding:32px 12px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"width:100%;max-width:560px;background:#ffffff;border-radius:16px;border:1px solid ").append(BORDER).append(";\">")
                .append("<tr><td style=\"background:").append(BRAND).append(";border-radius:16px 16px 0 0;padding:24px 32px;\">")
                .append("<div style=\"font-size:12px;letter-spacing:2px;text-transform:uppercase;color:#bfdbfe;font-weight:600;\">Requests</div>")
                .append("<div style=\"font-size:20px;font-weight:700;color:#ffffff;margin-top:6px;\">").append(html(companyName)).append("</div>")
                .append("</td></tr>")
                .append("<tr><td style=\"padding:32px;color:").append(TEXT).append(";\">")
                .append("<p style=\"margin:0;font-size:15px;line-height:1.65;\">Dear ").append(html(officerName)).append(",</p>")
                .append("<p style=\"margin:12px 0 0 0;font-size:15px;line-height:1.65;color:#334155;\">")
                .append("A new request has been submitted and is available for Finance review.</p>");
        if (link != null) {
            h.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:24px 0 0 0;\">")
                    .append("<tr><td style=\"background:").append(BRAND).append(";border-radius:10px;\">")
                    .append("<a href=\"").append(html(link)).append("\" target=\"_blank\" ")
                    .append("style=\"display:inline-block;padding:12px 28px;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:10px;\">")
                    .append("View requests</a></td></tr></table>")
                    .append("<p style=\"margin:14px 0 0 0;font-size:12px;line-height:1.6;color:").append(MUTED).append(";\">")
                    .append("Button not working? Copy this link into your browser:<br>")
                    .append("<a href=\"").append(html(link)).append("\" style=\"color:").append(BRAND).append(";word-break:break-all;\">")
                    .append(html(link)).append("</a></p>");
        } else {
            h.append("<p style=\"margin:12px 0 0 0;font-size:15px;line-height:1.65;color:#334155;\">Please log in and open Requests to review it.</p>");
        }
        h.append("<p style=\"margin:28px 0 0 0;font-size:15px;line-height:1.65;color:#334155;\">Regards,<br><strong>")
                .append(html(companyName)).append("</strong></p>")
                .append("</td></tr>")
                .append("<tr><td style=\"padding:0 32px 24px 32px;\"><div style=\"border-top:1px solid ").append(BORDER)
                .append(";padding-top:16px;text-align:center;font-size:12px;color:").append(MUTED).append(";\">")
                .append("This is an automated notification. Please do not reply to this email.</div></td></tr>")
                .append("</table></td></tr></table></body></html>");
        return h.toString();
    }

    private String buildText(String officerName, String companyName, String link) {
        StringBuilder t = new StringBuilder()
                .append("Dear ").append(officerName).append(",\n\n")
                .append("A new request has been submitted and is available for Finance review.\n\n");
        if (link != null) {
            t.append("View requests: ").append(link).append("\n\n");
        } else {
            t.append("Please log in and open Requests to review it.\n\n");
        }
        t.append("Regards,\n").append(companyName);
        return t.toString();
    }

    private String html(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}