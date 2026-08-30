package com.justjava.humanresource.communication.service;

import com.justjava.humanresource.communication.dto.PresenceResponse;
import com.justjava.humanresource.hr.entity.Employee;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    private final Map<Long, PresenceEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionEmployeeIds = new ConcurrentHashMap<>();

    public PresenceResponse markOnline(Employee employee, String sessionId) {
        sessionEmployeeIds.put(sessionId, employee.getId());
        PresenceEntry entry = entries.compute(employee.getId(), (id, current) -> {
            PresenceEntry next = current == null ? PresenceEntry.from(employee) : current;
            next.sessionIds.add(sessionId);
            next.online = true;
            next.lastSeenAt = LocalDateTime.now();
            return next;
        });
        return entry.toResponse();
    }

    public Optional<PresenceResponse> markOffline(String sessionId) {
        Long employeeId = sessionEmployeeIds.remove(sessionId);
        if (employeeId == null) {
            return Optional.empty();
        }
        PresenceEntry entry = entries.get(employeeId);
        if (entry == null) {
            return Optional.empty();
        }
        entry.sessionIds.remove(sessionId);
        entry.lastSeenAt = LocalDateTime.now();
        entry.online = !entry.sessionIds.isEmpty();
        return Optional.of(entry.toResponse());
    }

    public boolean isOnline(Long employeeId) {
        PresenceEntry entry = entries.get(employeeId);
        return entry != null && entry.online;
    }

    public List<Long> onlineEmployeeIds() {
        return entries.values().stream()
                .filter(entry -> entry.online)
                .map(entry -> entry.employeeId)
                .toList();
    }

    public List<PresenceResponse> getOnlineEmployees() {
        return entries.values().stream()
                .filter(entry -> entry.online)
                .map(PresenceEntry::toResponse)
                .sorted(Comparator.comparing(PresenceResponse::fullName))
                .toList();
    }

    private static final class PresenceEntry {
        private Long employeeId;
        private String employeeNumber;
        private String fullName;
        private String department;
        private boolean online;
        private LocalDateTime lastSeenAt;
        private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

        private static PresenceEntry from(Employee employee) {
            PresenceEntry entry = new PresenceEntry();
            entry.employeeId = employee.getId();
            entry.employeeNumber = employee.getEmployeeNumber();
            entry.fullName = employee.getFullName();
            entry.department = employee.getDepartment() == null ? "Unassigned" : employee.getDepartment().getName();
            entry.lastSeenAt = LocalDateTime.now();
            return entry;
        }

        private PresenceResponse toResponse() {
            return new PresenceResponse(employeeId, employeeNumber, fullName, department, online, lastSeenAt);
        }
    }
}
