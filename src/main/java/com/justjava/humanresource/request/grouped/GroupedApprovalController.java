package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.request.repository.WorkflowRequestTypeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GroupedApprovalController {
    private final GroupedApprovalConfigurationService configuration;
    private final WorkflowRequestTypeRepository requestTypes;
    private final GroupedApprovalService approvals;

    @GetMapping("/requests/grouped-approval-paths")
    public String page() { configuration.requireAdministrator(); return "request/grouped-approval-paths"; }

    @GetMapping("/api/grouped-approval-paths") @ResponseBody
    public Map<String,Object> list() {
        return Map.of("enabled", configuration.isEnabled(), "paths", configuration.list());
    }

    @GetMapping("/api/grouped-approval-paths/diagnostics") @ResponseBody
    public Map<String,Object> diagnostics() { return approvals.diagnostics(); }

    @PostMapping("/api/grouped-approval-paths") @ResponseBody
    public GroupedApprovalConfigurationService.VersionView create(@Valid @RequestBody GroupedApprovalCommand command) { return configuration.create(command); }

    @PutMapping("/api/grouped-approval-paths/{id}") @ResponseBody
    public GroupedApprovalConfigurationService.VersionView update(@PathVariable Long id, @RequestParam Long version,
            @Valid @RequestBody GroupedApprovalCommand command) { return configuration.update(id, version, command); }

    @PostMapping("/api/grouped-approval-paths/{id}/clone") @ResponseBody
    public GroupedApprovalConfigurationService.VersionView cloneDraft(@PathVariable Long id) { return configuration.cloneDraft(id); }

    @PostMapping("/api/grouped-approval-paths/{id}/publish") @ResponseBody
    public GroupedApprovalConfigurationService.VersionView publish(@PathVariable Long id, @RequestParam Long version) { return configuration.publish(id, version); }

    @PostMapping("/api/grouped-approval-paths/{id}/enabled") @ResponseBody
    public GroupedApprovalConfigurationService.VersionView enabled(@PathVariable Long id, @RequestParam boolean enabled) { return configuration.setEnabled(id, enabled); }

    @GetMapping("/api/grouped-approval-paths/{id}/preview") @ResponseBody
    public List<GroupedApprovalCommand.Level> preview(@PathVariable Long id, @RequestParam Long requesterId) {
        configuration.requireAdministrator(); return configuration.effectiveLevels(id, requesterId);
    }

    public record Binding(@NotNull Long pathVersionId) {}

    @PutMapping("/api/grouped-approval-paths/request-types/{code}") @ResponseBody @Transactional
    public void bind(@PathVariable String code, @Valid @RequestBody Binding binding) {
        configuration.requireAdministrator(); configuration.requireEnabled(); configuration.published(binding.pathVersionId());
        var type = requestTypes.findByCode(code.toUpperCase(java.util.Locale.ROOT)).orElseThrow();
        if (!type.isRequiresApproval()) throw new IllegalArgumentException("This request type does not require approval.");
        type.setApprovalRouteType(ApprovalRouteType.CUSTOM); type.setCustomApprovalPathId(null);
        type.setGroupedApprovalPathVersionId(binding.pathVersionId()); requestTypes.save(type);
    }
}
