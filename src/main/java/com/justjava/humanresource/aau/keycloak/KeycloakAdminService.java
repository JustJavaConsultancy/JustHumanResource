package com.justjava.humanresource.aau.keycloak;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KeycloakAdminService {

    private static final int DEFAULT_PAGE_SIZE = 100;

    private final Keycloak keycloak;
    private final String realmName;

    public KeycloakAdminService(
            Keycloak keycloak,
            @Value("${keycloak.realm}") String realmName
    ) {
        this.keycloak = keycloak;
        this.realmName = realmName;
    }

    /* ============================================================
       Internal Helpers
       ============================================================ */

    private RealmResource realm() {
        return keycloak.realm(realmName);
    }

    private UsersResource users() {
        return realm().users();
    }

    private RolesResource roles() {
        return realm().roles();
    }

    private GroupsResource groups() {
        return realm().groups();
    }

    private UserResource user(String userId) {
        return users().get(userId);
    }

    /* ============================================================
       User Creation
       ============================================================ */

    public String createUser(
            String username,
            String email,
            String password,
            String firstName,
            String lastName,
            Map<String, List<String>> attributes
    ) {
        Assert.hasText(username, "Username must not be empty");
        Assert.hasText(password, "Password must not be empty");

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmailVerified(true);

        if (attributes != null && !attributes.isEmpty()) {
            user.setAttributes(attributes);
        }

        user.setCredentials(Collections.singletonList(buildPasswordCredential(password)));

        try (Response response = users().create(user)) {

            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new IllegalStateException(
                        "Failed to create user. Status: "
                                + response.getStatus()
                                + " - "
                                + response.getStatusInfo().getReasonPhrase()
                );
            }

            return extractUserId(response.getLocation());
        }
    }

    private CredentialRepresentation buildPasswordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }

    private String extractUserId(URI location) {
        if (location == null) {
            throw new IllegalStateException("User created but no Location header returned.");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /* ============================================================
       User Management
       ============================================================ */

    public void enableUser(String userId) {
        UserRepresentation rep = user(userId).toRepresentation();
        rep.setEnabled(true);
        user(userId).update(rep);
    }

    public void disableUser(String userId) {
        UserRepresentation rep = user(userId).toRepresentation();
        rep.setEnabled(false);
        user(userId).update(rep);
    }

    public void deleteUser(String userId) {
        users().delete(userId);
    }

    public Optional<UserRepresentation> findByUsername(String username) {
        return users().search(username, 0, 1).stream().findFirst();
    }

    public Optional<UserRepresentation> findById(String userId) {
        try {
            return Optional.of(user(userId).toRepresentation());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /* ============================================================
       Password & Email Actions
       ============================================================ */

    public void sendPasswordResetEmail(String userId) {
        Assert.hasText(userId, "userId must not be empty");
        user(userId).executeActionsEmail(List.of("UPDATE_PASSWORD"));
    }

    public void sendVerifyEmail(String userId) {
        Assert.hasText(userId, "userId must not be empty");
        user(userId).executeActionsEmail(List.of("VERIFY_EMAIL"));
    }

    public void sendVerifyAndResetPasswordEmail(String userId) {
        Assert.hasText(userId, "userId must not be empty");
        user(userId).executeActionsEmail(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
    }

    public void resetPassword(String userId, String newPassword, boolean temporary) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(temporary);
        user(userId).resetPassword(credential);
    }

    /* ============================================================
       Role Management
       ============================================================ */

    public void assignRealmRole(String userId, String roleName) {
        RoleRepresentation role = roles().get(roleName).toRepresentation();
        user(userId).roles().realmLevel().add(List.of(role));
    }

    public void removeRealmRole(String userId, String roleName) {
        RoleRepresentation role = roles().get(roleName).toRepresentation();
        user(userId).roles().realmLevel().remove(List.of(role));
    }

    public List<String> getUserRealmRoles(String userId) {
        return user(userId)
                .roles()
                .realmLevel()
                .listAll()
                .stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toList());
    }

    /* ============================================================
       Group Management
       ============================================================ */

    public void addUserToGroup(String userId, String groupName) {

        GroupRepresentation group = groups().groups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Group not found: " + groupName)
                );

        user(userId).joinGroup(group.getId());
    }

    public void removeUserFromGroup(String userId, String groupName) {

        GroupRepresentation group = groups().groups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Group not found: " + groupName)
                );

        user(userId).leaveGroup(group.getId());
    }

    public List<String> getUserGroups(String userId) {
        return user(userId)
                .groups()
                .stream()
                .map(GroupRepresentation::getName)
                .collect(Collectors.toList());
    }

    /* ============================================================
       Listing Users
       ============================================================ */

    public List<UserRepresentation> listUsers(int firstResult, int maxResults) {
        return users().list(firstResult, maxResults);
    }

    public List<UserRepresentation> listAllUsers() {

        List<UserRepresentation> allUsers = new ArrayList<>();
        int first = 0;

        while (true) {
            List<UserRepresentation> batch = users().list(first, DEFAULT_PAGE_SIZE);

            if (batch.isEmpty()) break;

            allUsers.addAll(batch);

            if (batch.size() < DEFAULT_PAGE_SIZE) break;

            first += DEFAULT_PAGE_SIZE;
        }

        return allUsers;
    }

    /* ============================================================
       Searching
       ============================================================ */

    public List<UserRepresentation> searchUsers(
            String searchQuery,
            int firstResult,
            int maxResults
    ) {
        return users().search(searchQuery, firstResult, maxResults);
    }

    public List<UserRepresentation> searchUsersByAttribute(
            String attributeKey,
            String attributeValue
    ) {
        Assert.hasText(attributeKey, "Attribute key must not be empty");
        Assert.hasText(attributeValue, "Attribute value must not be empty");

        return users().searchByAttributes(attributeKey + ":" + attributeValue);
    }

    public List<UserRepresentation> searchUsersByAttributes(
            Map<String, String> attributes
    ) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyList();
        }

        String query = attributes.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        return users().searchByAttributes(query);
    }
}