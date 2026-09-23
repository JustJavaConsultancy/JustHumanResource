package com.justjava.humanresource.communication.meeting.service;

import com.justjava.humanresource.communication.meeting.entity.HrMeeting;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class MeetingInviteMessageBuilder {

    /** RFC 5322 requires CRLF line endings in the plain-text body. */
    private static final String CRLF = "\r\n";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy 'at' h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /* ------------------------------------------------------------------ */
    /*  Plain text                                                        */
    /* ------------------------------------------------------------------ */

    public String text(HrMeeting meeting) {
        StringBuilder b = new StringBuilder(256);

        b.append("You have been invited to a meeting.").append(CRLF)
                .append(CRLF)
                .append("Subject:  ").append(safe(meeting.getSubject())).append(CRLF)
                .append("Provider: ").append(providerLabel(meeting)).append(CRLF)
                .append("Time:     ").append(timeText(meeting)).append(CRLF);

        if (hasText(meeting.getAgenda())) {
            b.append(CRLF)
                    .append("Agenda").append(CRLF)
                    .append("------").append(CRLF)
                    .append(meeting.getAgenda().trim()).append(CRLF);
        }

        if (hasText(meeting.getJoinUrl())) {
            b.append(CRLF)
                    .append("Join the meeting:").append(CRLF)
                    .append(meeting.getJoinUrl().trim()).append(CRLF);
        }

        return b.toString();
    }

    /* ------------------------------------------------------------------ */
    /*  HTML                                                              */
    /* ------------------------------------------------------------------ */

    public String html(HrMeeting meeting) {
        String joinUrl = safeUrl(meeting.getJoinUrl());

        StringBuilder b = new StringBuilder(640);
        b.append("<div style=\"font-family:Arial,Helvetica,sans-serif;")
                .append("font-size:14px;line-height:1.55;color:#182033;\">");

        b.append("<p style=\"margin:0 0 14px 0;\">")
                .append("You have been invited to a meeting.")
                .append("</p>");

        // Summary table — labelled rows give each field its own line
        b.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"border-collapse:collapse;margin:0 0 16px 0;\">");
        summaryRow(b, "Subject",  escape(meeting.getSubject()));
        summaryRow(b, "Provider", escape(providerLabel(meeting)));
        summaryRow(b, "Time",     escape(timeText(meeting)));
        b.append("</table>");

        if (hasText(meeting.getAgenda())) {
            b.append("<p style=\"margin:0 0 4px 0;\"><strong>Agenda</strong></p>")
                    .append("<p style=\"margin:0 0 16px 0;\">")
                    .append(escape(meeting.getAgenda()).replace("\r\n", "\n").replace("\n", "<br>"))
                    .append("</p>");
        }

        if (!joinUrl.isEmpty()) {
            // Prominent call-to-action button
            b.append("<p style=\"margin:0 0 8px 0;\">")
                    .append("<a href=\"").append(joinUrl).append("\" ")
                    .append("style=\"display:inline-block;padding:10px 22px;")
                    .append("background:#2563eb;color:#ffffff;text-decoration:none;")
                    .append("border-radius:6px;font-weight:bold;\">")
                    .append("Join meeting")
                    .append("</a>")
                    .append("</p>");

            // Fallback link for clients that strip the button styling
            b.append("<p style=\"margin:0;font-size:12px;color:#737b8c;word-break:break-all;\">")
                    .append("Or copy this link: ")
                    .append("<a href=\"").append(joinUrl)
                    .append("\" style=\"color:#2563eb;text-decoration:underline;\">")
                    .append(joinUrl)
                    .append("</a>")
                    .append("</p>");
        }

        b.append("</div>");
        return b.toString();
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private void summaryRow(StringBuilder b, String label, String value) {
        b.append("<tr>")
                .append("<td style=\"padding:2px 14px 2px 0;color:#737b8c;")
                .append("font-size:12px;text-transform:uppercase;letter-spacing:.04em;")
                .append("vertical-align:top;white-space:nowrap;\">")
                .append(label)
                .append("</td>")
                .append("<td style=\"padding:2px 0;vertical-align:top;\">")
                .append(value.isEmpty() ? "&mdash;" : value)
                .append("</td>")
                .append("</tr>");
    }

    private String timeText(HrMeeting meeting) {
        if (meeting.isInstantMeeting()) {
            return "Now";
        }
        if (meeting.getStartTime() == null || meeting.getEndTime() == null) {
            return "Scheduled";
        }
        if (meeting.getStartTime().toLocalDate().equals(meeting.getEndTime().toLocalDate())) {
            return meeting.getStartTime().format(DATE_TIME_FORMATTER)
                    + " – "
                    + meeting.getEndTime().format(TIME_FORMATTER);
        }
        return meeting.getStartTime().format(DATE_TIME_FORMATTER)
                + " – "
                + meeting.getEndTime().format(DATE_TIME_FORMATTER);
    }

    private String providerLabel(HrMeeting meeting) {
        Object provider = meeting.getProvider();
        if (provider == null) {
            return "";
        }
        String name = provider.toString();
        return switch (name) {
            case "MICROSOFT_TEAMS" -> "Microsoft Teams";
            case "ZOOM"            -> "Zoom";
            case "GOOGLE_MEET"     -> "Google Meet";
            default                 -> name;
        };
    }

    /**
     * HTML-escapes the value. Also tolerates {@code null}.
     */
    private String escape(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }

    /**
     * Escapes a URL for use inside {@code href="..."} and rejects any scheme
     * other than http/https (defence against {@code javascript:} etc.).
     */
    private String safeUrl(String url) {
        if (!hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return escape(trimmed);
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}