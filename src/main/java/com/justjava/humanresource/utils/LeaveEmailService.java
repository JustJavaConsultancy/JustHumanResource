package com.justjava.humanresource.utils;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.leave.entity.LeaveRequest;
import com.justjava.humanresource.orgStructure.entity.Company;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveEmailService {

    private static final String DEFAULT_COMPANY_NAME = "Human Resources";

    private final EmployeeService employeeService;
    private final EmailService emailService;


    public void notifyLeaveSubmitted(LeaveRequest request) {
        notifyLeaveSubmitted(request, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyLeaveSubmitted(LeaveRequest request, Long currentApproverEmployeeId) {
        if (request == null) {
            log.warn("Leave email: notifyLeaveSubmitted called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getEmployeeId(), "leave submission requester");
        if (requester == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);

        String approverName = currentApproverEmployeeId != null
                ? employeeDisplayName(safeGetEmployee(currentApproverEmployeeId, "leave submission current approver"))
                : "";
        boolean hasApproverName = !approverName.isBlank();

        String employeeName = requester.getFullName();
        String subject = "Leave request received";

        StringBuilder text = new StringBuilder()
                .append("Dear ").append(employeeName).append(",\n\n")
                .append("Your leave request has been received and is now being processed.");
        if (hasApproverName) {
            text.append(" It is currently with ").append(approverName).append(" for approval.");
        }
        text.append(" You will be notified when a decision is made.\n\n")
                .append("Regards,\n").append(companyName);

        StringBuilder htmlBody = new StringBuilder()
                .append("<p>Dear ").append(html(employeeName)).append(",</p>")
                .append("<p>Your <strong>leave</strong> request has been received and is now being processed.");
        if (hasApproverName) {
            htmlBody.append(" It is currently with <strong>").append(html(approverName)).append("</strong> for approval.");
        }
        htmlBody.append(" You will be notified when a decision is made.</p>")
                .append("<p>Regards,<br><strong>").append(html(companyName)).append("</strong></p>");

        sendBestEffort(requester, subject, htmlBody.toString(), text.toString(), "leave submission notice");
    }

    // Requester-facing notice that the leave has moved to a new (next) approver.
    // Deliberately separate from notifyPendingApproval, which is the approver-facing
    // "you have work to do" email and must not be touched by this feature.
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyLeaveMovedToApprover(LeaveRequest request, Long currentApproverEmployeeId) {
        if (request == null) {
            log.warn("Leave email: notifyLeaveMovedToApprover called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getEmployeeId(), "leave movement requester");
        if (requester == null) {
            return;
        }

        Employee currentApprover = safeGetEmployee(currentApproverEmployeeId, "leave movement current approver");
        if (currentApprover == null) {
            log.warn("Leave email: skipping movement notice for leave request {} - current approver could not be resolved.",
                    request.getId());
            return;
        }

        String approverName = employeeDisplayName(currentApprover);
        if (approverName.isBlank()) {
            log.warn("Leave email: skipping movement notice for leave request {} - current approver has no usable name.",
                    request.getId());
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);

        String employeeName = requester.getFullName();
        String subject = "Leave request moved to next approver";
        String text = "Dear " + employeeName + ",\n\n"
                + "Your leave request has moved to the next approval stage. It is now with " + approverName + " for approval.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(employeeName) + ",</p>"
                + "<p>Your <strong>leave</strong> request has moved to the next approval stage. It is now with <strong>"
                + html(approverName) + "</strong> for approval.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(requester, subject, html, text, "leave moved to next approver notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyPendingApproval(LeaveRequest request, Long approverEmployeeId) {
        if (request == null) {
            log.warn("Leave email: notifyPendingApproval called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getEmployeeId(), "pending approval requester (for company branding)");
        if (requester == null) {
            return;
        }

        Employee approver = safeGetEmployee(approverEmployeeId, "pending approval approver");
        if (approver == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);

        String managerName = approver.getFullName();
        String subject = "Pending leave approval";
        String text = "Dear " + managerName + ",\n\n"
                + "You have a pending leave request awaiting your review. Please log in to view the details.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(managerName) + ",</p>"
                + "<p>You have a pending <strong>leave</strong> request awaiting your review. Please log in to view the details.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(approver, subject, html, text, "pending approval notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyLeaveApproved(LeaveRequest request) {
        if (request == null) {
            log.warn("Leave email: notifyLeaveApproved called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getEmployeeId(), "leave approval requester");
        if (requester == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);

        String employeeName = requester.getFullName();
        String subject = "Leave request approved";
        String text = "Dear " + employeeName + ",\n\n"
                + "Your leave request has been approved.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(employeeName) + ",</p>"
                + "<p>Your <strong>leave</strong> request has been approved.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(requester, subject, html, text, "leave approved notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyLeaveRejected(LeaveRequest request) {
        if (request == null) {
            log.warn("Leave email: notifyLeaveRejected called with a null request.");
            return;
        }

        Employee requester = safeGetEmployee(request.getEmployeeId(), "leave rejection requester");
        if (requester == null) {
            return;
        }

        Company company = resolveCompany(requester);
        String companyName = resolveCompanyName(company);

        String employeeName = requester.getFullName();
        String subject = "Leave request rejected";
        String text = "Dear " + employeeName + ",\n\n"
                + "Your leave request has been rejected.\n\n"
                + "Regards,\n" + companyName;
        String html = "<p>Dear " + html(employeeName) + ",</p>"
                + "<p>Your <strong>leave</strong> request has been rejected.</p>"
                + "<p>Regards,<br><strong>" + html(companyName) + "</strong></p>";

        sendBestEffort(requester, subject, html, text, "leave rejected notice");
    }

    private void sendBestEffort(Employee recipient, String subject, String html, String text, String context) {
        String email = recipient.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Leave email: skipping {} for employee {} - no email address on file.", context, recipient.getId());
            return;
        }

        try {
            emailService.sendEmail(email.trim(), subject, html, text);
        } catch (Exception e) {
            log.warn("Leave email: failed to send {} to employee {}: {}", context, recipient.getId(), e.getMessage());
        }
    }

    private Employee safeGetEmployee(Long employeeId, String context) {
        if (employeeId == null) {
            log.warn("Leave email: cannot resolve {} - employee id is null.", context);
            return null;
        }
        try {
            return employeeService.getById(employeeId);
        } catch (Exception e) {
            log.warn("Leave email: could not resolve {} (employee {}): {}", context, employeeId, e.getMessage());
            return null;
        }
    }


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
