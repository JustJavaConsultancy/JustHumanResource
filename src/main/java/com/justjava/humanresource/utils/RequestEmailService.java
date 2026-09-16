package com.justjava.humanresource.utils;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.orgStructure.entity.Company;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestEmailService {

    private static final String DEFAULT_COMPANY_NAME = "Human Resources";

    private final EmployeeService employeeService;
    private final EmailService emailService;




    public void notifyRequestSubmitted(WorkflowRequest request) {
        notifyRequestSubmitted(request, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyRequestSubmitted(WorkflowRequest request, Long currentApproverEmployeeId) {
        if (request == null) {
            log.warn("Request email: notifyRequestSubmitted called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getRequesterEmployeeId(), "request submission requester");
        if (requester == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);
        String requestTypePhrase = requestTypePhrase(request);

        String approverName = currentApproverEmployeeId != null
                ? employeeDisplayName(safeGetEmployee(currentApproverEmployeeId, "request submission current approver"))
                : "";
        boolean hasApproverName = !approverName.isBlank();

        String employeeName = requester.getFullName();
        String subject = "Request received";

        StringBuilder text = new StringBuilder()
                .append("Dear ").append(employeeName).append(",\n\n")
                .append("Your ").append(requestTypePhrase).append(" has been received and is now being processed.");
        if (hasApproverName) {
            text.append(" It is currently with ").append(approverName).append(" for approval.");
        }
        text.append(" You will be notified when a decision is made.\n\n")
                .append("Regards,\n").append(companyName);

        StringBuilder htmlBody = new StringBuilder()
                .append("<p>Dear ").append(html(employeeName)).append(",</p>")
                .append("<p>Your <strong>").append(html(requestTypePhrase)).append("</strong> has been received and is now being processed.");
        if (hasApproverName) {
            htmlBody.append(" It is currently with <strong>").append(html(approverName)).append("</strong> for approval.");
        }
        htmlBody.append(" You will be notified when a decision is made.</p>")
                .append("<p>Regards,<br><strong>").append(html(companyName)).append("</strong></p>");

        sendBestEffort(requester, subject, htmlBody.toString(), text.toString(), "request submission notice");
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyRequestMovedToApprover(WorkflowRequest request, Long currentApproverEmployeeId) {
        if (request == null) {
            log.warn("Request email: notifyRequestMovedToApprover called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getRequesterEmployeeId(), "request movement requester");
        if (requester == null) {
            return;
        }

        Employee currentApprover = safeGetEmployee(currentApproverEmployeeId, "request movement current approver");
        if (currentApprover == null) {
            log.warn("Request email: skipping movement notice for request {} - current approver could not be resolved.",
                    request.getId());
            return;
        }

        String approverName = employeeDisplayName(currentApprover);
        if (approverName.isBlank()) {
            log.warn("Request email: skipping movement notice for request {} - current approver has no usable name.",
                    request.getId());
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);
        String requestTypePhrase = requestTypePhrase(request);

        String employeeName = requester.getFullName();
        String subject = "Request moved to next approver";
        String text = "Dear " + employeeName + ",\n\n"
                + "Your " + requestTypePhrase + " has moved to the next approval stage. It is now with " + approverName + " for approval.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(employeeName) + ",</p>"
                + "<p>Your <strong>" + html(requestTypePhrase) + "</strong> has moved to the next approval stage. It is now with <strong>"
                + html(approverName) + "</strong> for approval.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(requester, subject, html, text, "request moved to next approver notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyPendingApproval(WorkflowRequest request, Long approverEmployeeId) {
        if (request == null) {
            log.warn("Request email: notifyPendingApproval called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getRequesterEmployeeId(), "pending approval requester (for company branding)");
        if (requester == null) {
            return;
        }

        Employee approver = safeGetEmployee(approverEmployeeId, "pending approval approver");
        if (approver == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);
        String requestTypePhrase = requestTypePhrase(request);

        String approverName = approver.getFullName();
        String subject = "Pending request approval";
        String text = "Dear " + approverName + ",\n\n"
                + "You have a pending " + requestTypePhrase + " awaiting your review. Please log in to view the details.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(approverName) + ",</p>"
                + "<p>You have a pending <strong>" + html(requestTypePhrase) + "</strong> awaiting your review. Please log in to view the details.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(approver, subject, html, text, "pending approval notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyRequestApproved(WorkflowRequest request) {
        if (request == null) {
            log.warn("Request email: notifyRequestApproved called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getRequesterEmployeeId(), "request approval requester");
        if (requester == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);
        String requestTypePhrase = requestTypePhrase(request);

        String employeeName = requester.getFullName();
        String subject = "Request approved";
        String text = "Dear " + employeeName + ",\n\n"
                + "Your " + requestTypePhrase + " has been approved.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(employeeName) + ",</p>"
                + "<p>Your <strong>" + html(requestTypePhrase) + "</strong> has been approved.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(requester, subject, html, text, "request approved notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyRequestRejected(WorkflowRequest request) {
        if (request == null) {
            log.warn("Request email: notifyRequestRejected called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getRequesterEmployeeId(), "request rejection requester");
        if (requester == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);
        String requestTypePhrase = requestTypePhrase(request);

        String employeeName = requester.getFullName();
        String subject = "Request rejected";
        String text = "Dear " + employeeName + ",\n\n"
                + "Your " + requestTypePhrase + " has been rejected.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(employeeName) + ",</p>"
                + "<p>Your <strong>" + html(requestTypePhrase) + "</strong> has been rejected.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(requester, subject, html, text, "request rejected notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyRequestReturnedForCorrection(WorkflowRequest request) {
        if (request == null) {
            log.warn("Request email: notifyRequestReturnedForCorrection called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getRequesterEmployeeId(), "request return-for-correction requester");
        if (requester == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);
        String requestTypePhrase = requestTypePhrase(request);

        String employeeName = requester.getFullName();
        String subject = "Request returned for correction";
        String text = "Dear " + employeeName + ",\n\n"
                + "Your " + requestTypePhrase + " has been returned for correction. Please log in to review the comments and resubmit when ready.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(employeeName) + ",</p>"
                + "<p>Your <strong>" + html(requestTypePhrase) + "</strong> has been returned for correction. Please log in to review the comments and resubmit when ready.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(requester, subject, html, text, "request returned-for-correction notice");
    }

    private void sendBestEffort(Employee recipient, String subject, String html, String text, String context) {
        String email = recipient.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Request email: skipping {} for employee {} - no email address on file.", context, recipient.getId());
            return;
        }

        try {
            emailService.sendEmail(email.trim(), subject, html, text);
        } catch (Exception e) {
            log.warn("Request email: failed to send {} to employee {}: {}", context, recipient.getId(), e.getMessage());
        }
    }

    private Employee safeGetEmployee(Long employeeId, String context) {
        if (employeeId == null) {
            log.warn("Request email: cannot resolve {} - employee id is null.", context);
            return null;
        }
        try {
            return employeeService.getById(employeeId);
        } catch (Exception e) {
            log.warn("Request email: could not resolve {} (employee {}): {}", context, employeeId, e.getMessage());
            return null;
        }
    }

    // Requirement: approver names shown to the requester must be exactly
    // firstName + " " + lastName - never employee number, department, or a
    // UI lookup label. Falls back to getFullName(), then to an empty string.
    private String employeeDisplayName(Employee employee) {
        if (employee == null) {
            return "";
        }
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String displayName = (firstName + " " + lastName).trim();
        if (!displayName.isBlank()) {
            return displayName;
        }
        return employee.getFullName() == null ? "" : employee.getFullName().trim();
    }

    private Company resolveCompany(Employee requester) {
        return requester.getDepartment() != null ? requester.getDepartment().getCompany() : null;
    }

    private String resolveCompanyName(Company company) {
        if (company != null && company.getName() != null && !company.getName().isBlank()) {
            return company.getName();
        }
        return DEFAULT_COMPANY_NAME;
    }

    private String requestTypePhrase(WorkflowRequest request) {
        String label = switch (request.getRequestType()) {
            case STAFF_REQUISITION -> "Staff Requisition";
            case FILE_REQUEST -> "File Request";
            case ASSET_REQUEST -> "Asset Request";
            case EXPENSE_REIMBURSEMENT -> "Expense Reimbursement";
            case GENERAL_REQUEST -> "General Request";
        };
        return label.toLowerCase(Locale.ROOT).endsWith("request")
                ? label
                : label + " request";
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
