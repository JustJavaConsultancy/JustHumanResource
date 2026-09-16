package com.justjava.humanresource.utils;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.service.JobHrEmployeeAccessService;
import com.justjava.humanresource.payroll.dto.PastPayslipEmailRequest;
import com.justjava.humanresource.payroll.dto.PayslipEmailRequest;
import com.justjava.humanresource.payroll.dto.PayslipEmailResponse;
import com.justjava.humanresource.payroll.dto.PayslipEmailResult;
import com.justjava.humanresource.payroll.entity.PaySlipDTO;
import com.justjava.humanresource.payroll.service.PaySlipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaySlipEmailService {
    private static final DateTimeFormatter PAYSLIP_MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter PAYSLIP_FILE_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PaySlipService paySlipService;
    private final EmployeeService employeeService;
    private final AuthenticationManager authenticationManager;
    private final JobHrEmployeeAccessService jobHrEmployeeAccessService;
    private final PaySlipPdfService paySlipPdfService;
    private final EmailService emailService;

    public PayslipEmailResponse emailCurrentPayslips(Long companyId, PayslipEmailRequest request) {
        List<Long> requestedEmployeeIds = request != null && request.employeeIds() != null
                ? request.employeeIds()
                : List.of();

        LinkedHashSet<Long> employeeIds = requestedEmployeeIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (employeeIds.isEmpty()) {
            return new PayslipEmailResponse(
                    0,
                    0,
                    0,
                    1,
                    List.of(new PayslipEmailResult(null, null, null, "FAILED", "Select at least one employee.", null))
            );
        }

        Set<Long> visibleEmployeeIds = paySlipService.getCurrentPeriodPayrollRuns(companyId).stream()
                .filter(run -> canAccessPayrollEmployee(run.getEmployee()))
                .peek(run -> {
                    if (employeeIds.contains(run.getEmployee().getId())
                            && run.getStatus() == PayrollRunStatus.POSTED
                            && !paySlipService.existsForPayrollRun(run.getId())) {
                        try {
                            paySlipService.generatePaySlip(run.getId());
                        } catch (Exception ignored) {
                        }
                    }
                })
                .map(run -> run.getEmployee().getId())
                .collect(Collectors.toSet());

        Map<Long, PaySlipDTO> currentPaySlipsByEmployee = paySlipService.getCurrentPeriodPaySlips(companyId).stream()
                .filter(ps -> visibleEmployeeIds.contains(ps.getEmployeeId()))
                .collect(Collectors.toMap(PaySlipDTO::getEmployeeId, ps -> ps, (first, second) -> first));

        List<PayslipEmailResult> results = new ArrayList<>();
        for (Long employeeId : employeeIds) {
            results.add(sendCurrentPayslip(employeeId, visibleEmployeeIds, currentPaySlipsByEmployee));
        }

        int sent = (int) results.stream().filter(result -> "SENT".equals(result.status())).count();
        int skipped = (int) results.stream().filter(result -> "SKIPPED".equals(result.status())).count();
        int failed = (int) results.stream().filter(result -> "FAILED".equals(result.status())).count();

        return new PayslipEmailResponse(employeeIds.size(), sent, skipped, failed, results);
    }

    public PayslipEmailResponse emailPastPayslips(Long companyId, PastPayslipEmailRequest request) {
        List<Long> requestedPaySlipIds = request != null && request.paySlipIds() != null
                ? request.paySlipIds()
                : List.of();

        LinkedHashSet<Long> paySlipIds = requestedPaySlipIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (paySlipIds.isEmpty()) {
            return new PayslipEmailResponse(
                    0,
                    0,
                    0,
                    1,
                    List.of(new PayslipEmailResult(null, null, null, "FAILED", "Select at least one payslip.", null))
            );
        }

        Map<Long, PaySlipDTO> paySlipsById = paySlipService.getAllClosedPeriodPaySlips(companyId).stream()
                .filter(ps -> paySlipIds.contains(ps.getId()))
                .collect(Collectors.toMap(PaySlipDTO::getId, ps -> ps, (first, second) -> first));

        List<PayslipEmailResult> results = new ArrayList<>();
        for (Long paySlipId : paySlipIds) {
            results.add(sendPastPayslip(paySlipsById.get(paySlipId)));
        }

        int sent = (int) results.stream().filter(result -> "SENT".equals(result.status())).count();
        int skipped = (int) results.stream().filter(result -> "SKIPPED".equals(result.status())).count();
        int failed = (int) results.stream().filter(result -> "FAILED".equals(result.status())).count();

        return new PayslipEmailResponse(paySlipIds.size(), sent, skipped, failed, results);
    }

    private PayslipEmailResult sendPastPayslip(PaySlipDTO paySlip) {
        if (paySlip == null) {
            return new PayslipEmailResult(null, null, null, "FAILED", "Payslip record was not found.", null);
        }

        Long employeeId = paySlip.getEmployeeId();
        Employee employee;
        try {
            employee = employeeService.getById(employeeId);
        } catch (Exception e) {
            return new PayslipEmailResult(employeeId, paySlip.getEmployeeName(), null, "FAILED", "Employee record was not found.", null);
        }

        String employeeName = employee.getFullName();
        String email = employee.getEmail();

        if (!canAccessPayrollEmployee(employee)) {
            return new PayslipEmailResult(employeeId, employeeName, email, "SKIPPED", "Employee is outside your payroll access scope.", null);
        }
        if (email == null || email.isBlank()) {
            return new PayslipEmailResult(employeeId, employeeName, email, "SKIPPED", "Employee has no email address.", null);
        }

        return sendPayslipEmail(employeeId, employeeName, email, paySlip);
    }

    private PayslipEmailResult sendCurrentPayslip(
            Long employeeId,
            Set<Long> visibleEmployeeIds,
            Map<Long, PaySlipDTO> currentPaySlipsByEmployee
    ) {
        Employee employee;
        try {
            employee = employeeService.getById(employeeId);
        } catch (Exception e) {
            return new PayslipEmailResult(employeeId, null, null, "FAILED", "Employee record was not found.", null);
        }

        String employeeName = employee.getFullName();
        String email = employee.getEmail();

        if (!visibleEmployeeIds.contains(employeeId)) {
            return new PayslipEmailResult(employeeId, employeeName, email, "SKIPPED", "Employee is outside your payroll access scope.", null);
        }
        if (email == null || email.isBlank()) {
            return new PayslipEmailResult(employeeId, employeeName, email, "SKIPPED", "Employee has no email address.", null);
        }

        PaySlipDTO paySlip = currentPaySlipsByEmployee.get(employeeId);
        if (paySlip == null) {
            return new PayslipEmailResult(employeeId, employeeName, email, "SKIPPED", "No current payslip is available for this employee.", null);
        }

        return sendPayslipEmail(employeeId, employeeName, email, paySlip);
    }

    private PayslipEmailResult sendPayslipEmail(Long employeeId, String employeeName, String email, PaySlipDTO paySlip) {
        try {
            byte[] pdf = paySlipPdfService.generate(paySlip);
            String month = paySlip.getPayDate() != null ? paySlip.getPayDate().format(PAYSLIP_MONTH_FORMAT) : "the selected period";
            String filename = "payslip_" + employeeId + "_" + (paySlip.getPayDate() != null
                    ? paySlip.getPayDate().format(PAYSLIP_FILE_MONTH_FORMAT)
                    : "current") + ".pdf";
            String subject = "Your payslip for " + month;
            String text = "Dear " + employeeName + ",\n\nPlease find attached your payslip for " + month + ".\n\nRegards,\nHuman Resources";
            String html = "<p>Dear " + html(employeeName) + ",</p>"
                    + "<p>Please find attached your payslip for " + html(month) + ".</p>"
                    + "<p>Regards,<br>Human Resources</p>";

            String emailMessageId = emailService.sendPdfAttachment(
                    email.trim(),
                    subject,
                    html,
                    text,
                    filename,
                    pdf
            );
            return new PayslipEmailResult(employeeId, employeeName, email, "SENT", "Email sent successfully.", emailMessageId);
        } catch (Exception e) {
            return new PayslipEmailResult(employeeId, employeeName, email, "FAILED", e.getMessage(), null);
        }
    }

    private boolean canAccessPayrollEmployee(Employee employee) {
        if (employee == null) {
            return false;
        }
        if (authenticationManager.isRestrictedHr() && employee.isRestrictedVisibility()) {
            return false;
        }
        if (jobHrEmployeeAccessService.isJobHrScopedUser()) {
            Long actorGradeId = jobHrEmployeeAccessService.getLoggedInJobGradeId();
            Long employeeGradeId = employee.getJobStep() != null && employee.getJobStep().getJobGrade() != null
                    ? employee.getJobStep().getJobGrade().getId()
                    : null;
            return Objects.equals(actorGradeId, employeeGradeId);
        }
        return true;
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
