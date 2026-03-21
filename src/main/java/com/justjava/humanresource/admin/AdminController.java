package com.justjava.humanresource.admin;

import com.justjava.humanresource.aau.keycloak.KeycloakAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    KeycloakAdminService keycloakAdminService;

    private static final int DEFAULT_PAGE_SIZE = 10;

    /* ══════════════════════════════════════════════════════════
       Dashboard — User List (with search + pagination)
       ══════════════════════════════════════════════════════════ */

    @GetMapping("/users")
    public String dashboard(
            @RequestParam(defaultValue = "")  String search,
            @RequestParam(defaultValue = "0") int    page,
            @RequestParam(defaultValue = ""  + DEFAULT_PAGE_SIZE) int size,
            Model model
    ) {
        // ── Fetch ──────────────────────────────────────────────
        List<UserRepresentation> allUsers = (search == null || search.isBlank())
                ? keycloakAdminService.listAllUsers("humanResources")
                : keycloakAdminService.searchUsers("humanResources",search, 0, 500);

        // ── Aggregate stats ────────────────────────────────────
        int  totalUsers  = allUsers.size();
        long totalActive = allUsers.stream()
                .filter(u -> Boolean.TRUE.equals(u.isEnabled()))
                .count();

        // ── Paginate ───────────────────────────────────────────
        int totalPages = totalUsers == 0 ? 1 : (int) Math.ceil((double) totalUsers / size);
        int safePage   = Math.max(0, Math.min(page, totalPages - 1));
        int from       = safePage * size;
        int to         = Math.min(from + size, totalUsers);

        List<UserRepresentation> pageUsers =
                from < totalUsers ? allUsers.subList(from, to) : Collections.emptyList();

        // ── Model ──────────────────────────────────────────────
        model.addAttribute("users",       pageUsers);
        model.addAttribute("totalUsers",  totalUsers);
        model.addAttribute("totalActive", totalActive);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages",  totalPages);
        model.addAttribute("pageSize",    size);
        model.addAttribute("search",      search);
        model.addAttribute("fromIndex",   totalUsers == 0 ? 0 : from + 1);
        model.addAttribute("toIndex",     to);

        return "admin/userManagement";
    }

    /* ══════════════════════════════════════════════════════════
       Create User
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/create-user")
    public String createUser(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String firstName,
            @RequestParam String lastName,
            HttpServletRequest  request,
            RedirectAttributes  ra
    ) {
        try {
            keycloakAdminService.createUser(
                    "humanResources",username, email, password,
                    firstName, lastName,
                    parseAttributes(request)
            );
            ra.addFlashAttribute("successMessage",
                    "User \"" + username + "\" was created successfully.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage",
                    "Could not create user: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Enable / Disable
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/enable-user")
    public String enableUser(@RequestParam String userId, RedirectAttributes ra) {
        try {
            keycloakAdminService.enableUser("humanResources",userId);
            ra.addFlashAttribute("successMessage", "User account enabled.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to enable user: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/disable-user")
    public String disableUser(@RequestParam String userId, RedirectAttributes ra) {
        try {
            keycloakAdminService.disableUser("humanResources",userId);
            ra.addFlashAttribute("successMessage", "User account disabled.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to disable user: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Delete
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/delete-user")
    public String deleteUser(@RequestParam String userId, RedirectAttributes ra) {
        try {
            keycloakAdminService.deleteUser("humanResources",userId);
            ra.addFlashAttribute("successMessage", "User deleted successfully.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to delete user: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Password Actions
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/reset-password-email")
    public String sendPasswordResetEmail(@RequestParam String userId, RedirectAttributes ra) {
        try {
            keycloakAdminService.sendPasswordResetEmail("humanResources",userId);
            ra.addFlashAttribute("successMessage", "Password reset email sent.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Could not send reset email: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Role Management
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/assign-role")
    public String assignRole(
            @RequestParam String userId,
            @RequestParam String roleName,
            RedirectAttributes ra
    ) {
        try {
            keycloakAdminService.assignRealmRole("humanResources",userId, roleName);
            ra.addFlashAttribute("successMessage", "Role \"" + roleName + "\" assigned.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Role assignment failed: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/remove-role")
    public String removeRole(
            @RequestParam String userId,
            @RequestParam String roleName,
            RedirectAttributes ra
    ) {
        try {
            keycloakAdminService.removeRealmRole("humanResources",userId, roleName);
            ra.addFlashAttribute("successMessage", "Role \"" + roleName + "\" removed.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Role removal failed: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Group Management
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/add/group")
    public String addUserToGroup(
            @RequestParam String userId,
            @RequestParam String groupName,
            RedirectAttributes ra
    ) {
        try {
            keycloakAdminService.addUserToGroup("humanResources",userId, groupName);
            ra.addFlashAttribute("successMessage", "User added to group \"" + groupName + "\".");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to add to group: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/remove/group")
    public String removeUserFromGroup(
            @RequestParam String userId,
            @RequestParam String groupName,
            RedirectAttributes ra
    ) {
        try {
            keycloakAdminService.removeUserFromGroup("humanResources",userId, groupName);
            ra.addFlashAttribute("successMessage", "User removed from group \"" + groupName + "\".");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to remove from group: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       JSON API — consumed by the Manage modal via fetch()
       ══════════════════════════════════════════════════════════ */

    /** Returns the list of group names the user belongs to. */
    @GetMapping("/user/{userId}/groups")
    @ResponseBody
    public ResponseEntity<List<String>> getUserGroups(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(keycloakAdminService.getUserGroups("humanResources",userId));
        } catch (Exception ex) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /** Returns the list of realm role names assigned to the user. */
    @GetMapping("/user/{userId}/roles")
    @ResponseBody
    public ResponseEntity<List<String>> getUserRoles(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(keycloakAdminService.getUserRealmRoles("humanResources",userId));
        } catch (Exception ex) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /* ══════════════════════════════════════════════════════════
       Helpers
       ══════════════════════════════════════════════════════════ */

    /**
     * Parses form parameters of the pattern {@code attribute[KEY][INDEX]}
     * into a {@code Map<String, List<String>>} for Keycloak user attributes.
     *
     * <p>Example: {@code attribute[department][0]=Engineering} becomes
     * {@code {"department": ["Engineering"]}}.
     */
    private Map<String, List<String>> parseAttributes(HttpServletRequest request) {
        Map<String, List<String>> result  = new LinkedHashMap<>();
        Pattern                  pattern = Pattern.compile("^attribute\\[(.+?)]\\[\\d+]$");

        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Matcher m = pattern.matcher(entry.getKey());
            if (m.matches()) {
                String key = m.group(1);
                result.computeIfAbsent(key, k -> new ArrayList<>())
                        .addAll(Arrays.asList(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * Strips HTML tags and truncates exception messages before they reach
     * flash attributes — prevents accidental XSS in error toasts.
     */
    private String sanitize(String msg) {
        if (msg == null) return "An unexpected error occurred.";
        String clean = msg.replaceAll("<[^>]+>", "");
        return clean.length() > 250 ? clean.substring(0, 247) + "…" : clean;
    }
}