package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.request.service.WorkflowRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/requests/grouped-approvals")
@RequiredArgsConstructor
public class GroupedRequestApprovalController {
    private final GroupedApprovalService grouped;
    private final WorkflowRequestService requests;
    private final com.justjava.humanresource.core.config.AuthenticationManager authentication;

    @GetMapping("/csrf")
    public Map<String,String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }

    @GetMapping("/{id}")
    public Map<String,Object> details(@PathVariable Long id) {
        var detail = authentication.isFinancialOfficer() ? requests.financeDetails(id) : requests.details(id);
        return Map.of("cycles", detail.getGroupedApprovalCycles(), "requestStatus", detail.getRequest().getStatus());
    }

    public record Decision(@NotBlank String taskId, @NotBlank String action, @Size(max=2000) String comment) {}

    @PostMapping("/decisions")
    public GroupedApprovalService.DecisionResult decide(@Valid @RequestBody Decision command) {
        return grouped.decide(command.taskId(), command.action(), command.comment(), requests.currentEmployee().getId());
    }
}
