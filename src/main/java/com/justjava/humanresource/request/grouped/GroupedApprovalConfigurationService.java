package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupedApprovalConfigurationService {
    private final GroupedApprovalVersionRepository versions;
    private final GroupedApprovalDefinitionRepository definitions;
    private final EmployeeRepository employees;
    private final EmployeeService employeeService;
    private final AuthenticationManager auth;

    @Value("${app.requests.grouped-approval.enabled:true}")
    private boolean enabled;

    public boolean isEnabled() { return enabled; }

    public void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "New grouped approval submissions are disabled. Existing approvals remain available.");
    }

    public void requireAdministrator() {
        if (!(auth.isHumanResource() || auth.isJobHR() || auth.isRestrictedHr() || auth.isAdmin()))
            throw new AccessDeniedException("HR or administrator access is required.");
    }

    public Long actorId() {
        Object email = auth.get("email");
        if (email == null) throw new AccessDeniedException("An authenticated employee is required.");
        return employeeService.getByEmail(email.toString()).getId();
    }

    public Employee eligibleEmployee(Long id) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approver employee not found: " + id));
        if (employee.getEmploymentStatus() != EmploymentStatus.ACTIVE || employee.isRestrictedVisibility()
                || employee.getStatus() == RecordStatus.SUSPENDED)
            throw new IllegalArgumentException("Approver must be an active visible employee: " + id);
        return employee;
    }

    @Transactional(readOnly=true)
    public List<VersionView> list() {
        requireAdministrator();
        return versions.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional
    public VersionView create(GroupedApprovalCommand command) {
        requireAdministrator(); requireEnabled(); validate(command);
        GroupedApprovalVersion version = new GroupedApprovalVersion();
        version.setFamilyKey(UUID.randomUUID().toString()); version.setRevision(1);
        return save(version, command);
    }

    @Transactional
    public VersionView update(Long id, Long expectedVersion, GroupedApprovalCommand command) {
        requireAdministrator(); requireEnabled(); validate(command);
        GroupedApprovalVersion version = locked(id);
        if (!"DRAFT".equals(version.getState())) throw conflict("Published versions are immutable. Clone a new draft.");
        if (!Objects.equals(expectedVersion, version.getVersion())) throw conflict("This draft changed. Refresh before saving.");
        definitions.deleteByPathVersionId(id);
        definitions.flush();
        return save(version, command);
    }

    @Transactional
    public VersionView cloneDraft(Long id) {
        requireAdministrator(); requireEnabled();
        GroupedApprovalVersion source = versions.findById(id).orElseThrow();
        // A family lock serializes revision allocation, including cloning older published revisions.
        GroupedApprovalVersion familyRoot = versions.findAllByOrderByCreatedAtDesc().stream()
                .filter(v -> v.getFamilyKey().equals(source.getFamilyKey()))
                .min(Comparator.comparing(GroupedApprovalVersion::getRevision)).orElseThrow();
        versions.lockById(familyRoot.getId()).orElseThrow();
        int revision = versions.findAllByOrderByCreatedAtDesc().stream()
                .filter(v -> v.getFamilyKey().equals(source.getFamilyKey()))
                .mapToInt(GroupedApprovalVersion::getRevision).max().orElse(0) + 1;
        GroupedApprovalVersion next = new GroupedApprovalVersion();
        next.setFamilyKey(source.getFamilyKey()); next.setRevision(revision);
        return save(next, new GroupedApprovalCommand(source.getName(), source.getDescription(),
                definitions.findByPathVersionIdOrderByOrdinal(id).stream()
                        .map(d -> new GroupedApprovalCommand.Level(d.getName(), d.getPolicy(), List.copyOf(d.getEmployeeIds())))
                        .toList()));
    }

    @Transactional
    public VersionView publish(Long id, Long expectedVersion) {
        requireAdministrator(); requireEnabled();
        GroupedApprovalVersion version = locked(id);
        if (!"DRAFT".equals(version.getState()) || !Objects.equals(expectedVersion, version.getVersion()))
            throw conflict("The draft changed or was already published. Refresh before publishing.");
        VersionView current = view(version);
        validate(new GroupedApprovalCommand(version.getName(), version.getDescription(), current.levels()));
        version.setState("PUBLISHED"); version.setPublishedAt(LocalDateTime.now()); version.setPublishedBy(actorId());
        return view(versions.saveAndFlush(version));
    }

    @Transactional
    public VersionView setEnabled(Long id, boolean value) {
        requireAdministrator();
        GroupedApprovalVersion version = locked(id);
        version.setEnabled(value);
        return view(versions.saveAndFlush(version));
    }

    @Transactional(readOnly=true)
    public GroupedApprovalVersion published(Long id) {
        GroupedApprovalVersion version = versions.findById(id).orElseThrow(() -> new IllegalArgumentException("Grouped path not found."));
        if (!version.isEnabled() || !"PUBLISHED".equals(version.getState()))
            throw conflict("The selected grouped path must be enabled and published.");
        return version;
    }

    @Transactional(readOnly=true)
    public List<GroupedApprovalCommand.Level> effectiveLevels(Long id, Long requesterId) {
        published(id);
        List<GroupedApprovalCommand.Level> result = new ArrayList<>();
        for (GroupedApprovalDefinition definition : definitions.findByPathVersionIdOrderByOrdinal(id)) {
            List<Long> members = definition.getEmployeeIds().stream().filter(member -> !member.equals(requesterId)).toList();
            if (members.isEmpty()) throw conflict("Level '" + definition.getName() + "' has no eligible approvers after excluding the requester.");
            members.forEach(this::eligibleEmployee);
            result.add(new GroupedApprovalCommand.Level(definition.getName(), definition.getPolicy(), members));
        }
        if (result.isEmpty()) throw conflict("The grouped approval path has no levels.");
        return result;
    }

    private void validate(GroupedApprovalCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.name().length() > 100
                || command.description() != null && command.description().length() > 500
                || command.levels() == null || command.levels().isEmpty() || command.levels().size() > 20)
            throw new IllegalArgumentException("A name and 1 to 20 approval levels are required.");
        Set<Long> seen = new HashSet<>();
        for (var level : command.levels()) {
            if (level.name() == null || level.name().isBlank() || level.name().length() > 100 || level.policy() == null
                    || level.employeeIds() == null || level.employeeIds().isEmpty() || level.employeeIds().size() > 50)
                throw new IllegalArgumentException("Each level needs a name, policy and 1 to 50 approvers.");
            for (Long id : level.employeeIds()) {
                if (id == null || !seen.add(id)) throw new IllegalArgumentException("An approver cannot appear more than once in a path.");
                eligibleEmployee(id);
            }
        }
    }

    private VersionView save(GroupedApprovalVersion version, GroupedApprovalCommand command) {
        version.setName(command.name().trim()); version.setDescription(command.description());
        version.setUpdatedAt(LocalDateTime.now());
        version = versions.saveAndFlush(version);
        int ordinal = 1;
        for (var level : command.levels()) {
            GroupedApprovalDefinition definition = new GroupedApprovalDefinition();
            definition.setPathVersionId(version.getId()); definition.setOrdinal(ordinal++);
            definition.setName(level.name().trim()); definition.setPolicy(level.policy());
            definition.setEmployeeIds(new ArrayList<>(level.employeeIds())); definitions.save(definition);
        }
        definitions.flush();
        return view(version);
    }

    private GroupedApprovalVersion locked(Long id) { return versions.lockById(id).orElseThrow(); }
    private VersionView view(GroupedApprovalVersion v) {
        return new VersionView(v.getId(), v.getVersion(), v.getName(), v.getDescription(), v.getRevision(), v.getState(), v.isEnabled(),
                definitions.findByPathVersionIdOrderByOrdinal(v.getId()).stream()
                        .map(d -> new GroupedApprovalCommand.Level(d.getName(), d.getPolicy(), List.copyOf(d.getEmployeeIds()))).toList());
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    public record VersionView(Long id, Long version, String name, String description, Integer revision, String state,
                              boolean enabled, List<GroupedApprovalCommand.Level> levels) {}
}
