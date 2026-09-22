package com.justjava.humanresource.communication.meeting.service;

import com.justjava.humanresource.communication.meeting.entity.HrMeeting;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class MeetingInviteMessageBuilder {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public String text(HrMeeting meeting) {
        StringBuilder builder = new StringBuilder();
        builder.append("You have been invited to a meeting.\n\n")
                .append("Subject: ").append(meeting.getSubject()).append('\n')
                .append("Provider: ").append(meeting.getProvider()).append('\n')
                .append("Time: ").append(timeText(meeting)).append('\n');
        if (meeting.getAgenda() != null && !meeting.getAgenda().isBlank()) {
            builder.append("\nAgenda:\n").append(meeting.getAgenda()).append('\n');
        }
        builder.append("\nJoin: ").append(meeting.getJoinUrl());
        return builder.toString();
    }

    public String html(HrMeeting meeting) {
        return """
                <p>You have been invited to a meeting.</p>
                <p><strong>Subject:</strong> %s</p>
                <p><strong>Provider:</strong> %s</p>
                <p><strong>Time:</strong> %s</p>
                %s
                <p><a href="%s">Join meeting</a></p>
                """.formatted(
                escape(meeting.getSubject()),
                meeting.getProvider(),
                escape(timeText(meeting)),
                meeting.getAgenda() == null || meeting.getAgenda().isBlank()
                        ? ""
                        : "<p><strong>Agenda:</strong><br>" + escape(meeting.getAgenda()).replace("\n", "<br>") + "</p>",
                escape(meeting.getJoinUrl())
        );
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
                    + " - "
                    + meeting.getEndTime().format(TIME_FORMATTER);
        }
        return meeting.getStartTime().format(DATE_TIME_FORMATTER)
                + " - "
                + meeting.getEndTime().format(DATE_TIME_FORMATTER);
    }

    private String escape(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }
}
