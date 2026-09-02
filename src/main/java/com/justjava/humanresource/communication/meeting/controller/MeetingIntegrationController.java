package com.justjava.humanresource.communication.meeting.controller;

import com.justjava.humanresource.communication.meeting.dto.MeetingIntegrationStatusResponse;
import com.justjava.humanresource.communication.meeting.service.MeetingIntegrationStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class MeetingIntegrationController {

    private final MeetingIntegrationStatusService meetingIntegrationStatusService;

    @GetMapping("/api/communication/meeting-integrations/status")
    @ResponseBody
    public List<MeetingIntegrationStatusResponse> statuses() {
        return meetingIntegrationStatusService.listStatuses();
    }
}
