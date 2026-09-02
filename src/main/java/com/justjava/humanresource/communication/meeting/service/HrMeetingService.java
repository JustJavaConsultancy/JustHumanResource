package com.justjava.humanresource.communication.meeting.service;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.communication.meeting.config.MeetingIntegrationProperties;
import com.justjava.humanresource.communication.meeting.dto.CreateHrMeetingCommand;
import com.justjava.humanresource.communication.meeting.dto.HrMeetingParticipantResponse;
import com.justjava.humanresource.communication.meeting.dto.HrMeetingResponse;
import com.justjava.humanresource.communication.meeting.entity.HrMeeting;
import com.justjava.humanresource.communication.meeting.entity.HrMeetingParticipant;
import com.justjava.humanresource.communication.meeting.entity.HrMeetingStatus;
import com.justjava.humanresource.communication.meeting.provider.CreateMeetingRequest;
import com.justjava.humanresource.communication.meeting.provider.CreatedMeeting;
import com.justjava.humanresource.communication.meeting.provider.MeetingProviderClient;
import com.justjava.humanresource.communication.meeting.provider.MeetingProviderException;
import com.justjava.humanresource.communication.meeting.repository.HrMeetingParticipantRepository;
import com.justjava.humanresource.communication.meeting.repository.HrMeetingRepository;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrMeetingService {

    private final AuthenticationManager authenticationManager;
    private final MeetingIntegrationProperties properties;
    private final List<MeetingProviderClient> providerClients;
    private final HrMeetingRepository meetingRepository;
    private final HrMeetingParticipantRepository participantRepository;
    private final EmployeeRepository employeeRepository;
    private final MeetingNotificationService meetingNotificationService;

    @Transactional(readOnly = true)
    public List<HrMeetingResponse> listMeetings() {
        requireHrOrAdmin();
        List<HrMeeting> meetings = meetingRepository.findByStatusOrderByStartTimeDesc(HrMeetingStatus.SCHEDULED);
        Map<Long, List<HrMeetingParticipant>> participantsByMeetingId = participantRepository
                .findByMeeting_IdInOrderByEmployeeNameAsc(meetings.stream().map(HrMeeting::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(participant -> participant.getMeeting().getId()));
        return meetings.stream()
                .map(meeting -> toResponse(meeting, participantsByMeetingId.getOrDefault(meeting.getId(), List.of()), true))
                .toList();
    }

    @Transactional(readOnly = true)
    public HrMeetingResponse getMeeting(Long meetingId) {
        requireHrOrAdmin();
        HrMeeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("Meeting not found"));
        return toResponse(meeting, participantRepository.findByMeeting_IdOrderByEmployeeNameAsc(meetingId), true);
    }

    @Transactional(readOnly = true)
    public List<HrMeetingResponse> listCurrentEmployeeMeetings() {
        Employee employee = currentEmployee();
        return participantRepository.findByEmployee_IdOrderByMeeting_StartTimeDesc(employee.getId()).stream()
                .map(HrMeetingParticipant::getMeeting)
                .filter(meeting -> meeting.getStatus() == HrMeetingStatus.SCHEDULED)
                .distinct()
                .map(meeting -> toResponse(meeting, participantRepository.findByMeeting_IdOrderByEmployeeNameAsc(meeting.getId()), false))
                .toList();
    }

    @Transactional
    public HrMeetingResponse createMeeting(CreateHrMeetingCommand command) {
        requireHrOrAdmin();
        MeetingProvider provider = command.provider() == null ? properties.getDefaults().getProvider() : command.provider();
        LocalDateTime start = resolveStart(command);
        LocalDateTime end = resolveEnd(command, start);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Meeting end time must be after start time");
        }

        List<Employee> participants = resolveParticipants(command.participantEmployeeIds());
        MeetingProviderClient client = providerClient(provider);
        CreatedMeeting created = client.createMeeting(new CreateMeetingRequest(
                provider,
                normalize(command.subject(), 200),
                blankToNull(command.agenda(), 2000),
                start.atZone(ZoneId.of(properties.getDefaults().getTimezone())),
                end.atZone(ZoneId.of(properties.getDefaults().getTimezone())),
                command.instantMeeting(),
                participants.stream().map(Employee::getEmail).filter(Objects::nonNull).filter(email -> !email.isBlank()).toList()
        ));
        if (created.joinUrl() == null || created.joinUrl().isBlank()) {
            throw new MeetingProviderException(provider + " did not return a join URL");
        }

        HrMeeting meeting = new HrMeeting();
        meeting.setProvider(provider);
        meeting.setSubject(normalize(command.subject(), 200));
        meeting.setAgenda(blankToNull(command.agenda(), 2000));
        meeting.setStartTime(start);
        meeting.setEndTime(end);
        meeting.setInstantMeeting(command.instantMeeting());
        meeting.setExternalMeetingId(created.externalMeetingId());
        meeting.setJoinUrl(created.joinUrl());
        meeting.setHostUrl(created.hostUrl());
        Object name = authenticationManager.get("name");
        meeting.setCreatedByName(name == null ? "HR" : String.valueOf(name));
        Object email = authenticationManager.get("email");
        meeting.setCreatedByEmail(email == null ? null : String.valueOf(email));
        meeting.setProviderResponseSummary(created.providerResponseSummary());
        HrMeeting saved = meetingRepository.save(meeting);

        List<HrMeetingParticipant> savedParticipants = participants.stream()
                .sorted(Comparator.comparing(Employee::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(employee -> saveParticipant(saved, employee))
                .toList();
        if (command.notifyParticipants()) {
            savedParticipants = meetingNotificationService.notifyParticipants(saved, savedParticipants);
        }
        return toResponse(saved, savedParticipants, true);
    }

    @Transactional
    public HrMeetingResponse cancelMeeting(Long meetingId) {
        requireHrOrAdmin();
        HrMeeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("Meeting not found"));
        meeting.setStatus(HrMeetingStatus.CANCELLED);
        HrMeeting saved = meetingRepository.save(meeting);
        return toResponse(saved, participantRepository.findByMeeting_IdOrderByEmployeeNameAsc(meetingId), true);
    }

    private HrMeetingParticipant saveParticipant(HrMeeting meeting, Employee employee) {
        HrMeetingParticipant participant = new HrMeetingParticipant();
        participant.setMeeting(meeting);
        participant.setEmployee(employee);
        participant.setEmployeeName(employee.getFullName());
        participant.setEmployeeEmail(employee.getEmail());
        participant.setEmployeeNumber(employee.getEmployeeNumber());
        participant.setNotified(false);
        participant.setChatStatus("PENDING");
        participant.setEmailStatus("PENDING");
        return participantRepository.save(participant);
    }

    private List<Employee> resolveParticipants(List<Long> participantEmployeeIds) {
        if (participantEmployeeIds == null || participantEmployeeIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = participantEmployeeIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        List<Employee> employees = employeeRepository.findAllById(ids).stream()
                .filter(employee -> employee.getEmploymentStatus() == EmploymentStatus.ACTIVE)
                .filter(employee -> !employee.isRestrictedVisibility())
                .toList();
        if (employees.size() != ids.size()) {
            throw new IllegalArgumentException("One or more selected participants are not available");
        }
        return employees;
    }

    private MeetingProviderClient providerClient(MeetingProvider provider) {
        Map<MeetingProvider, MeetingProviderClient> clients = new EnumMap<>(MeetingProvider.class);
        providerClients.forEach(client -> clients.put(client.provider(), client));
        MeetingProviderClient client = clients.get(provider);
        if (client == null || !client.isConfigured()) {
            throw new MeetingProviderException(provider + " meeting provider is not configured");
        }
        return client;
    }

    private LocalDateTime resolveStart(CreateHrMeetingCommand command) {
        if (command.instantMeeting()) {
            return LocalDateTime.now();
        }
        if (command.startTime() == null) {
            throw new IllegalArgumentException("Meeting start time is required");
        }
        return command.startTime();
    }

    private LocalDateTime resolveEnd(CreateHrMeetingCommand command, LocalDateTime start) {
        if (command.endTime() != null) {
            return command.endTime();
        }
        return start.plus(properties.getDefaults().getDuration());
    }

    private HrMeetingResponse toResponse(HrMeeting meeting, List<HrMeetingParticipant> participants, boolean includeHostUrl) {
        return new HrMeetingResponse(
                meeting.getId(),
                meeting.getProvider(),
                meeting.getSubject(),
                meeting.getAgenda(),
                meeting.getStartTime(),
                meeting.getEndTime(),
                meeting.isInstantMeeting(),
                meeting.getExternalMeetingId(),
                meeting.getJoinUrl(),
                includeHostUrl ? meeting.getHostUrl() : null,
                meeting.getCreatedByName(),
                meeting.getCreatedByEmail(),
                meeting.getStatus(),
                participants.stream().map(this::toParticipantResponse).toList()
        );
    }

    private HrMeetingParticipantResponse toParticipantResponse(HrMeetingParticipant participant) {
        Employee employee = participant.getEmployee();
        return new HrMeetingParticipantResponse(
                employee == null ? null : employee.getId(),
                participant.getEmployeeName(),
                participant.getEmployeeEmail(),
                participant.getEmployeeNumber(),
                participant.isNotified(),
                participant.isChatNotified(),
                participant.getChatStatus(),
                participant.isEmailNotified(),
                participant.getEmailStatus(),
                participant.getNotificationError()
        );
    }

    private Employee currentEmployee() {
        Object email = authenticationManager.get("email");
        if (email == null || String.valueOf(email).isBlank()) {
            throw new AccessDeniedException("Authenticated user has no email claim");
        }
        return employeeRepository.findByEmail(String.valueOf(email))
                .orElseThrow(() -> new EntityNotFoundException("No employee profile found for " + email));
    }

    private void requireHrOrAdmin() {
        if (!(authenticationManager.isHumanResource() || authenticationManager.isAdmin())) {
            throw new AccessDeniedException("Only HR and admin users can manage meetings.");
        }
    }

    private String normalize(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Meeting subject is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Value is too long");
        }
        return normalized;
    }

    private String blankToNull(String value, int maxLength) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Value is too long");
        }
        return normalized;
    }
}
