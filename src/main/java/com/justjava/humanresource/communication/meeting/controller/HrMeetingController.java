package com.justjava.humanresource.communication.meeting.controller;

import com.justjava.humanresource.communication.meeting.dto.CreateHrMeetingCommand;
import com.justjava.humanresource.communication.meeting.dto.HrMeetingResponse;
import com.justjava.humanresource.communication.meeting.service.HrMeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HrMeetingController {

    private final HrMeetingService meetingService;

    @GetMapping("/communication/meetings")
    @ResponseBody
    public List<HrMeetingResponse> meetings() {
        return meetingService.listMeetings();
    }

    @PostMapping("/communication/meetings")
    @ResponseBody
    public ResponseEntity<HrMeetingResponse> createMeeting(@Valid @RequestBody CreateHrMeetingCommand command) {
        return ResponseEntity.ok(meetingService.createMeeting(command));
    }

    @GetMapping("/communication/meetings/{meetingId}")
    @ResponseBody
    public HrMeetingResponse meeting(@PathVariable Long meetingId) {
        return meetingService.getMeeting(meetingId);
    }

    @PostMapping("/communication/meetings/{meetingId}/cancel")
    @ResponseBody
    public ResponseEntity<HrMeetingResponse> cancelMeeting(@PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.cancelMeeting(meetingId));
    }

    @GetMapping("/employee/communication/meetings")
    @ResponseBody
    public List<HrMeetingResponse> employeeMeetings() {
        return meetingService.listCurrentEmployeeMeetings();
    }
}
