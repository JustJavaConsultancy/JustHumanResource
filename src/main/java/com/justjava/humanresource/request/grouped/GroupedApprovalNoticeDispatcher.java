package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import com.justjava.humanresource.utils.EmailService;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** Durable grouped notifications. Legacy notification dispatch is unchanged. */
@Component @RequiredArgsConstructor @Slf4j
public class GroupedApprovalNoticeDispatcher {
    private final GroupedApprovalNoticeRepository notices;
    private final GroupedApprovalAssignmentRepository assignments;
    private final GroupedApprovalCycleRepository cycles;
    private final WorkflowRequestRepository requests;
    private final EmailService email;
    private final EmployeeRepository employees;

    @Transactional
    public void dispatch() {
        for (var notice : notices.findTop20ByDeliveredAtIsNullAndNextAttemptAtBeforeOrderById(LocalDateTime.now())) {
            try {
                var request = requests.findById(notice.getRequestId()).orElseThrow();
                var cycle = cycles.findById(notice.getCycleId()).orElseThrow();
                boolean send = !"SUPERSEDED".equals(cycle.getState());
                if ("PENDING".equals(notice.getKind())) {
                    var assignment = assignments.findById(notice.getAssignmentId()).orElseThrow();
                    send &= "PENDING".equals(assignment.getState()) && request.getStatus() == com.justjava.humanresource.request.enums.RequestStatus.IN_APPROVAL;
                }
                if (send) {
                    Long recipient = notice.getEmployeeId() == null ? request.getRequesterEmployeeId() : notice.getEmployeeId();
                    var employee = employees.findById(recipient).orElseThrow();
                    String outcome = switch (notice.getKind()) {
                        case "PENDING" -> "awaits your approval";
                        case "SUBMITTED" -> "was submitted for grouped approval";
                        case "APPROVED" -> "was approved";
                        case "REJECTED" -> "was rejected";
                        case "RETURNED" -> "was returned for correction";
                        default -> throw new IllegalStateException("Unknown notice kind.");
                    };
                    String text = "Request " + request.getRequestNumber() + " " + outcome + ". Sign in to JustHR to view its current status.";
                    // Use the throwing transport directly. Legacy best-effort helpers deliberately swallow send failures.
                    email.sendEmail(employee.getEmail(), "Request " + request.getRequestNumber() + ": " + outcome,
                            "<p>" + org.springframework.web.util.HtmlUtils.htmlEscape(text) + "</p>", text);
                }
                notice.setDeliveredAt(LocalDateTime.now()); notice.setLastError(null);
            } catch (Exception ex) {
                notice.setAttempts(notice.getAttempts() + 1);
                notice.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(3600, 30L << Math.min(7, notice.getAttempts()))));
                notice.setLastError(ex.getClass().getSimpleName());
                log.warn("Grouped notification {} failed (attempt {})", notice.getId(), notice.getAttempts());
            }
            notices.save(notice);
        }
    }
}
