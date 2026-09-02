package com.justjava.humanresource.communication.meeting.service;

import com.justjava.humanresource.communication.meeting.entity.HrMeeting;
import org.springframework.stereotype.Component;

@Component
public class MeetingInviteMessageBuilder {

    public String text(HrMeeting meeting) {
        StringBuilder builder = new StringBuilder();
        builder.append("You have been invited to a meeting.\n\n")
                .append("Subject: ").append(meeting.getSubject()).append('\n')
                .append("Provider: ").append(meeting.getProvider()).append('\n')
                .append("Starts: ").append(meeting.getStartTime()).append('\n')
                .append("Ends: ").append(meeting.getEndTime()).append('\n');
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
                <p><strong>Starts:</strong> %s</p>
                <p><strong>Ends:</strong> %s</p>
                %s
                <p><a href="%s">Join meeting</a></p>
                """.formatted(
                escape(meeting.getSubject()),
                meeting.getProvider(),
                meeting.getStartTime(),
                meeting.getEndTime(),
                meeting.getAgenda() == null || meeting.getAgenda().isBlank()
                        ? ""
                        : "<p><strong>Agenda:</strong><br>" + escape(meeting.getAgenda()).replace("\n", "<br>") + "</p>",
                escape(meeting.getJoinUrl())
        );
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
