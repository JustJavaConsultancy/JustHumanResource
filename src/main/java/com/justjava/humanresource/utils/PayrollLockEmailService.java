package com.justjava.humanresource.utils;

import com.justjava.humanresource.aau.keycloak.KeycloakAdminService;
import com.justjava.humanresource.orgStructure.entity.Company;
import com.justjava.humanresource.orgStructure.repositories.CompanyRepository;
import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollRunService;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;


@Slf4j
@Service
public class PayrollLockEmailService {

    private static final String DEFAULT_COMPANY_NAME = "Human Resources";
    private static final String DEFAULT_OFFICER_NAME = "Finance Officer";
    private static final DateTimeFormatter RANGE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter NAME_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter REF_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ENGLISH);

    // Brand palette (inline CSS only - email clients ignore most stylesheets)
    private static final String BRAND = "#1e3a8a";
    private static final String BRAND_LIGHT = "#eff6ff";
    private static final String TEXT = "#0f172a";
    private static final String MUTED = "#64748b";
    private static final String BORDER = "#e2e8f0";
    private static final String FONT = "'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private final EmailService emailService;
    private final KeycloakAdminService keycloakAdminService;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollRunService payrollRunService;
    private final CompanyRepository companyRepository;

    private final String realm;
    private final String financeGroupName;
    private final String appUrl;
    private final List<String> logoProperties;

    public PayrollLockEmailService(
            EmailService emailService,
            KeycloakAdminService keycloakAdminService,
            PayrollPeriodRepository payrollPeriodRepository,
            PayrollRunService payrollRunService,
            CompanyRepository companyRepository,
            @Value("${keycloak.base-realm}") String realm,
            @Value("${app.mail.finance-group:financialofficers}") String financeGroupName,
            @Value("${app.mail.app-url:${app.tracking.base-url:}}") String appUrl,
            @Value("${app.mail.logo-properties:logoUrl,logo,logoPath,logoImageUrl}") String logoProperties
    ) {
        this.emailService = emailService;
        this.keycloakAdminService = keycloakAdminService;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payrollRunService = payrollRunService;
        this.companyRepository = companyRepository;
        this.realm = realm;
        this.financeGroupName = financeGroupName;
        this.appUrl = appUrl == null ? "" : appUrl.trim();
        this.logoProperties = Arrays.stream(logoProperties.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
    }

    /* ============================================================
       PUBLIC ENTRY POINTS
       ============================================================ */

    /** Kept for callers that only have the basic workflow variables. */
    public void notifyFinanceOfficersPeriodLocked(Long companyId, Long periodId, Integer employeeCountHint) {
        notifyFinanceOfficersPeriodLocked(companyId, periodId, employeeCountHint, null, null, null);
    }

    /**
     * @param companyId           company that owns the period (falls back to the period's own company id)
     * @param periodId            the locked payroll period
     * @param employeeCountHint   optional employee count from the workflow
     * @param totalGrossHint      optional period gross from the workflow ("totalGross" variable)
     * @param totalDeductionsHint optional period deductions from the workflow ("totalDeductions" variable)
     * @param totalNetHint        optional period net from the workflow ("totalNet" variable)
     */
    public void notifyFinanceOfficersPeriodLocked(Long companyId,
                                                  Long periodId,
                                                  Integer employeeCountHint,
                                                  BigDecimal totalGrossHint,
                                                  BigDecimal totalDeductionsHint,
                                                  BigDecimal totalNetHint) {
        try {
            if (periodId == null) {
                log.warn("Lock email: periodId is null - nothing to send.");
                return;
            }

            PayrollPeriod period = payrollPeriodRepository.findById(periodId).orElse(null);
            if (period == null) {
                log.warn("Lock email: payroll period {} not found - nothing to send.", periodId);
                return;
            }

            Long resolvedCompanyId = companyId != null ? companyId : period.getCompanyId();
            PayrollLockSummary summary = buildSummary(
                    resolvedCompanyId, period, employeeCountHint,
                    totalGrossHint, totalDeductionsHint, totalNetHint);

            Map<String, UserRepresentation> officers = loadFinanceOfficers();
            if (officers.isEmpty()) {
                log.warn("Lock email: no enabled users with an email address in group '{}' (realm '{}').",
                        financeGroupName, realm);
                return;
            }

            String subject = "Action required: " + summary.periodName() + " payroll is locked and awaiting your approval";

            int sent = 0;
            for (Map.Entry<String, UserRepresentation> entry : officers.entrySet()) {
                String email = entry.getKey();
                String name = displayName(entry.getValue());
                try {
                    emailService.sendEmail(email, subject, buildHtml(name, summary), buildText(name, summary));
                    sent++;
                } catch (Exception e) {
                    log.warn("Lock email: failed to send to {}: {}", email, e.getMessage());
                }
            }
            log.info("Lock email: sent to {} of {} finance officer(s) for period {}.",
                    sent, officers.size(), summary.periodName());
        } catch (Exception e) {
            log.warn("Lock email: could not notify finance officers for period {}: {}", periodId, e.getMessage(), e);
        }
    }

    /* ============================================================
       DATA
       ============================================================ */

    private PayrollLockSummary buildSummary(Long companyId,
                                            PayrollPeriod period,
                                            Integer employeeCountHint,
                                            BigDecimal totalGrossHint,
                                            BigDecimal totalDeductionsHint,
                                            BigDecimal totalNetHint) {

        LocalDate start = period.getPeriodStart();
        LocalDate end = period.getPeriodEnd();

        // Posted runs of THIS period (same source the reconciliation step uses).
        List<PayrollRunDTO> runs = payrollRunService.getPayrollRunsForPeriod(companyId, start, end);
        log.info("Lock email: {} posted payroll run(s) found for company {} period {} -> {}.",
                runs.size(), companyId, start, end);

        /* ---- Period figures: prefer what the Lock Approval page shows ---- */
        BigDecimal gross = totalGrossHint != null ? totalGrossHint : sum(runs, PayrollRunDTO::getGrossPay);
        BigDecimal deductions = totalDeductionsHint != null ? totalDeductionsHint : sum(runs, PayrollRunDTO::getTotalDeductions);
        BigDecimal net = totalNetHint != null ? totalNetHint : sum(runs, PayrollRunDTO::getNetPay);

        int employees;
        if (employeeCountHint != null && employeeCountHint > 0) {
            employees = employeeCountHint;
        } else if (!runs.isEmpty()) {
            employees = runs.size();
        } else {
            employees = payrollRunService.getCurrentPeriodPayrollRuns(companyId).size();
        }

        /* ---- PAYE & pension for the locked period only ---- */
        BigDecimal paye = sum(runs, PayrollRunDTO::getPaye);
        BigDecimal pension = sum(runs, PayrollRunDTO::getPension);

        log.info("Lock email: summary employees={}, gross={}, deductions={}, net={}, paye={}, pension={}.",
                employees, gross, deductions, net, paye, pension);

        return new PayrollLockSummary(
                resolveCompanyName(companyId),
                start.format(NAME_FORMAT),
                start,
                end,
                employees,
                gross,
                deductions,
                net,
                paye,
                pension,
                resolveLogoUrl(companyId)
        );
    }

    private <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> getter) {
        if (rows == null) {
            return BigDecimal.ZERO;
        }
        return rows.stream()
                .map(getter)
                .map(v -> v != null ? v : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** email (lower-case) -> user; de-duplicated, enabled users with an email only. */
    private Map<String, UserRepresentation> loadFinanceOfficers() {
        Map<String, UserRepresentation> result = new LinkedHashMap<>();
        List<UserRepresentation> members = keycloakAdminService.getUsersInGroup(realm, financeGroupName);
        for (UserRepresentation user : members) {
            if (!Boolean.TRUE.equals(user.isEnabled())) {
                continue;
            }
            String email = user.getEmail();
            if (email == null || email.isBlank()) {
                log.warn("Lock email: skipping user '{}' - no email address on file.", user.getUsername());
                continue;
            }
            result.putIfAbsent(email.trim().toLowerCase(Locale.ROOT), user);
        }
        return result;
    }

    private String resolveCompanyName(Long companyId) {
        try {
            return companyRepository.findById(companyId)
                    .map(Company::getName)
                    .filter(n -> n != null && !n.isBlank())
                    .orElse(DEFAULT_COMPANY_NAME);
        } catch (Exception e) {
            log.warn("Lock email: could not resolve company {}: {}", companyId, e.getMessage());
            return DEFAULT_COMPANY_NAME;
        }
    }

    /**
     * Returns an absolute http(s) URL for the company logo, or null when the company has no
     * usable logo (in which case the email simply renders without one).
     *
     * <p>The logo property on {@link Company} is looked up by name from
     * {@code app.mail.logo-properties} (default: logoUrl, logo, logoPath, logoImageUrl) so this
     * class does not depend on the exact field name. Only text values are supported: a full
     * http(s) URL, or a path starting with "/" which is prefixed with {@code app.mail.app-url}.</p>
     */
    private String resolveLogoUrl(Long companyId) {
        if (companyId == null) {
            return null;
        }
        try {
            Company company = companyRepository.findById(companyId).orElse(null);
            if (company == null) {
                return null;
            }
            BeanWrapper wrapper = new BeanWrapperImpl(company);
            for (String property : logoProperties) {
                if (!wrapper.isReadableProperty(property)) {
                    continue;
                }
                Object value = wrapper.getPropertyValue(property);
                if (value == null) {
                    continue;
                }
                if (value instanceof String text) {
                    String url = toAbsoluteLogoUrl(text.trim());
                    if (url != null) {
                        return url;
                    }
                } else {
                    log.info("Lock email: company logo property '{}' is of type {} - only URL/path text is supported, logo skipped.",
                            property, value.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            log.warn("Lock email: could not resolve logo for company {}: {}", companyId, e.getMessage());
        }
        return null;
    }

    private String toAbsoluteLogoUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return value;
        }
        if (value.startsWith("/") && !appUrl.isBlank()) {
            String base = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
            return base + value;
        }
        log.info("Lock email: company logo value '{}' is not an absolute URL and cannot be made one - logo skipped.", value);
        return null;
    }

    private String displayName(UserRepresentation user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        if (!full.isBlank()) {
            return full;
        }
        return user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername().trim()
                : DEFAULT_OFFICER_NAME;
    }

    /* ============================================================
       RENDERING - HTML
       ============================================================ */

    private String buildHtml(String officerName, PayrollLockSummary s) {
        String range = s.periodStart().format(RANGE_FORMAT) + " &ndash; " + s.periodEnd().format(RANGE_FORMAT);
        String company = html(s.companyName());

        StringBuilder h = new StringBuilder(7000);
        h.append("<!DOCTYPE html><html lang=\"en\"><head>")
                .append("<meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Payroll period awaiting approval</title>")
                .append("<style>")
                .append("table,td{box-sizing:border-box;}")
                .append("img{max-width:100%;}")
                .append("@media only screen and (max-width:620px){")
                .append(".outer{padding:16px 8px !important;}")
                .append(".px{padding-left:20px !important;padding-right:20px !important;}")
                .append(".cell{padding:12px 14px !important;word-break:break-word;}")
                .append("}")
                .append("</style></head>")
                .append("<body style=\"margin:0;padding:0;background:#f1f5f9;font-family:").append(FONT).append(";\">");

        // Preheader (inbox preview text)
        h.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:#f1f5f9;\">")
                .append(html(s.periodName())).append(" payroll has been locked and is waiting for your approval.")
                .append("</div>");

        h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:#f1f5f9;\">")
                .append("<tr><td class=\"outer\" align=\"center\" style=\"padding:32px 12px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"width:100%;max-width:600px;box-sizing:border-box;background:#ffffff;border-radius:16px;border:1px solid ").append(BORDER).append(";\">");

        // Header band
        h.append("<tr><td class=\"px\" style=\"background:").append(BRAND).append(";border-radius:16px 16px 0 0;padding:28px 36px;\">");

        // Logo - rendered only when the company has one. White chip keeps dark logos readable on the blue band.
        String logoUrl = s.logoUrl() == null ? "" : s.logoUrl().trim();
        if (!logoUrl.isBlank()) {
            h.append("<div style=\"margin-bottom:14px;\">")
                    .append("<span style=\"display:inline-block;background:#ffffff;border-radius:10px;padding:8px 12px;\">")
                    .append("<img src=\"").append(html(logoUrl)).append("\" alt=\"").append(company)
                    .append("\" height=\"40\" style=\"display:block;height:40px;width:auto;max-width:180px;border:0;\">")
                    .append("</span></div>");
        }

        h.append("<div style=\"font-size:12px;letter-spacing:2px;text-transform:uppercase;color:#bfdbfe;font-weight:600;\">Payroll Approval</div>")
                .append("<div style=\"font-size:22px;font-weight:700;color:#ffffff;margin-top:6px;\">").append(company).append("</div>")
                .append("</td></tr>");

        // Body
        h.append("<tr><td class=\"px\" style=\"padding:36px 36px 8px 36px;color:").append(TEXT).append(";\">");

        h.append("<span style=\"display:inline-block;background:#fef3c7;color:#92400e;font-size:11px;font-weight:700;")
                .append("letter-spacing:1px;text-transform:uppercase;padding:5px 12px;border-radius:999px;\">Action required</span>");

        h.append("<h1 style=\"margin:16px 0 0 0;font-size:24px;line-height:1.3;font-weight:700;color:").append(TEXT).append(";\">")
                .append("A payroll period is awaiting your approval</h1>");

        h.append("<p style=\"margin:20px 0 0 0;font-size:15px;line-height:1.65;color:").append(TEXT).append(";\">Dear ")
                .append(html(officerName)).append(",</p>");

        h.append("<p style=\"margin:12px 0 0 0;font-size:15px;line-height:1.65;color:#334155;\">")
                .append("HR has locked the <strong>").append(html(s.periodName())).append("</strong> payroll period and submitted it for your review. ")
                .append("You have a pending locked period that needs your approval before it can be finalised. ")
                .append("Here is a summary of what was locked.</p>");

        // Period card
        h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:24px;\">")
                .append("<tr><td style=\"background:").append(BRAND_LIGHT).append(";border:1px solid #bfdbfe;border-radius:12px;padding:20px 24px;\">")
                .append("<div style=\"font-size:11px;letter-spacing:1.5px;text-transform:uppercase;color:").append(BRAND).append(";font-weight:700;\">Payroll period</div>")
                .append("<div style=\"font-size:22px;font-weight:700;color:").append(TEXT).append(";margin-top:6px;\">").append(html(s.periodName())).append("</div>")
                .append("<div style=\"font-size:14px;color:").append(MUTED).append(";margin-top:4px;\">").append(range).append("</div>")
                .append("</td></tr></table>");

        // Payroll summary (this period)
        h.append(sectionTitle("Payroll summary"));
        h.append(boxStart());
        h.append(row("Total employees", String.valueOf(s.totalEmployees()), false, false));
        h.append(row("Gross pay", naira(s.grossPay()), false, false));
        h.append(row("Total deductions", naira(s.totalDeductions()), false, false));
        h.append(row("Net pay", naira(s.netPay()), true, true));
        h.append(boxEnd());

        // Statutory remittances (locked period only)
        h.append(sectionTitle("Statutory remittances"));
        h.append(boxStart());
        h.append(row("PAYE tax remittance", naira(s.payeTax()), false, false));
        h.append(row("Employee pension fund", naira(s.employeePension()), false, true));
        h.append(boxEnd());

        // CTA
        if (!appUrl.isBlank()) {
            h.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\" style=\"margin:32px auto 0 auto;\">")
                    .append("<tr><td align=\"center\" style=\"background:").append(BRAND).append(";border-radius:10px;\">")
                    .append("<a href=\"").append(html(appUrl)).append("\" target=\"_blank\" ")
                    .append("style=\"display:inline-block;padding:14px 34px;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:10px;\">")
                    .append("Open the application &rarr;</a></td></tr></table>");
            h.append("<p style=\"margin:16px 0 0 0;text-align:center;font-size:12px;line-height:1.6;color:").append(MUTED).append(";\">")
                    .append("Button not working? Copy this link into your browser:<br>")
                    .append("<a href=\"").append(html(appUrl)).append("\" style=\"color:").append(BRAND).append(";word-break:break-all;\">")
                    .append(html(appUrl)).append("</a></p>");
        }

        h.append("<p style=\"margin:32px 0 0 0;font-size:15px;line-height:1.65;color:#334155;\">Regards,<br><strong>")
                .append(company).append("</strong></p>");

        h.append("</td></tr>");

        // Footer
        // The reference line is unique per email. Gmail collapses trailing text that repeats an earlier
        // message in the same thread behind "show trimmed content"; a unique last line prevents that.
        String reference = "Ref: " + java.time.LocalDateTime.now().format(REF_FORMAT)
                + "-" + Long.toString(System.nanoTime() % 1_000_000L, 36).toUpperCase(Locale.ROOT);

        h.append("<tr><td class=\"px\" style=\"padding:24px 36px 32px 36px;\">")
                .append("<div style=\"border-top:1px solid ").append(BORDER).append(";padding-top:20px;text-align:center;\">")
                .append("<div style=\"font-size:14px;font-weight:700;color:").append(TEXT).append(";\">").append(company).append("</div>")
                .append("<div style=\"font-size:12px;color:").append(MUTED).append(";margin-top:6px;line-height:1.6;\">")
                .append("This is an automated notification. Please do not reply to this email.</div>")
                .append("<div style=\"font-size:11px;color:").append(MUTED).append(";margin-top:10px;\">")
                .append(html(reference)).append("</div>")
                .append("</div></td></tr>");

        h.append("</table></td></tr></table></body></html>");
        return h.toString();
    }

    /**
     * Bordered rounded box. The border sits on a wrapper cell (not on the table itself) so a
     * 100%-wide table can never become 100% + border and overflow on narrow screens.
     */
    private String boxStart() {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:100%;\">"
                + "<tr><td style=\"border:1px solid " + BORDER + ";border-radius:12px;overflow:hidden;padding:0;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:100%;\">";
    }

    private String boxEnd() {
        return "</table></td></tr></table>";
    }

    private String sectionTitle(String title) {
        // title may already contain safe entities (e.g. &ndash;), so it is not escaped again.
        return "<div style=\"margin:28px 0 10px 0;font-size:13px;letter-spacing:1.2px;text-transform:uppercase;"
                + "font-weight:700;color:" + MUTED + ";\">" + title + "</div>";
    }

    private String row(String label, String value, boolean highlight, boolean last) {
        String bg = highlight ? BRAND_LIGHT : "#ffffff";
        String border = last ? "" : "border-bottom:1px solid " + BORDER + ";";
        String valueColor = highlight ? BRAND : TEXT;
        String valueSize = highlight ? "18px" : "15px";
        return "<tr>"
                + "<td class=\"cell\" style=\"padding:14px 20px;background:" + bg + ";" + border + "font-size:14px;color:#475569;\">" + html(label) + "</td>"
                + "<td class=\"cell\" align=\"right\" style=\"padding:14px 20px;background:" + bg + ";" + border
                + "font-size:" + valueSize + ";font-weight:700;color:" + valueColor + ";\">" + value + "</td>"
                + "</tr>";
    }

    /** HTML-safe naira amount (uses the numeric entity so no charset issues in any client). */
    private String naira(BigDecimal value) {
        return "&#8358;" + amount(value);
    }

    private String amount(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.US, "%,.2f", v);
    }

    /* ============================================================
       RENDERING - PLAIN TEXT
       ============================================================ */

    private String buildText(String officerName, PayrollLockSummary s) {
        StringBuilder t = new StringBuilder()
                .append("Dear ").append(officerName).append(",\n\n")
                .append("HR has locked the ").append(s.periodName())
                .append(" payroll period and it is now pending your approval.\n\n")
                .append("PAYROLL PERIOD\n")
                .append(s.periodName()).append(" (")
                .append(s.periodStart().format(RANGE_FORMAT)).append(" - ")
                .append(s.periodEnd().format(RANGE_FORMAT)).append(")\n\n")
                .append("PAYROLL SUMMARY\n")
                .append("Total employees       : ").append(s.totalEmployees()).append('\n')
                .append("Gross pay             : NGN ").append(amount(s.grossPay())).append('\n')
                .append("Total deductions      : NGN ").append(amount(s.totalDeductions())).append('\n')
                .append("Net pay               : NGN ").append(amount(s.netPay())).append("\n\n")
                .append("STATUTORY REMITTANCES\n")
                .append("PAYE tax remittance   : NGN ").append(amount(s.payeTax())).append('\n')
                .append("Employee pension fund : NGN ").append(amount(s.employeePension())).append("\n\n");
        if (!appUrl.isBlank()) {
            t.append("Open the application: ").append(appUrl).append("\n\n");
        }
        t.append("Regards,\n").append(s.companyName());
        return t.toString();
    }

    /* ============================================================
       ESCAPING
       ============================================================ */

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