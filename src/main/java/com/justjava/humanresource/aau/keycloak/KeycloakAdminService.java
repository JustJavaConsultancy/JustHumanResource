package com.justjava.humanresource.aau.keycloak;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KeycloakAdminService {

    private static final int DEFAULT_PAGE_SIZE = 100;

    private final Keycloak adminKeycloak;
    private final Keycloak baseKeycloak;
    private final String realmName;
    private final String baseRealmName;

    public KeycloakAdminService(
            @Qualifier("adminKeycloak") Keycloak adminKeycloak,
            @Qualifier("baseKeycloak") Keycloak baseKeycloak,
            @Value("${keycloak.realm}") String realmName,
            @Value("${keycloak.base-realm}") String baseRealmName
    ) {
        this.adminKeycloak = adminKeycloak;
        this.baseKeycloak = baseKeycloak;
        this.realmName = realmName;
        this.baseRealmName = baseRealmName;
    }

    /* ============================================================
       Internal Helpers
       ============================================================ */

    private RealmResource realm(Keycloak keycloak, String realm) {
        return keycloak.realm(realm);
    }

    private UsersResource users(Keycloak keycloak, String realm) {
        return realm(keycloak, realm).users();
    }

    private RolesResource roles(Keycloak keycloak, String realm) {
        return realm(keycloak, realm).roles();
    }

    private GroupsResource groups(Keycloak keycloak, String realm) {
        return realm(keycloak, realm).groups();
    }

    private UserResource user(Keycloak keycloak, String realm, String userId) {
        return users(keycloak, realm).get(userId);
    }

    /* ============================================================
       User Creation
       ============================================================ */

    public String createUser(
            String realm,
            String username,
            String email,
            String password,
            String firstName,
            String lastName,
            Map<String, List<String>> attributes
    ) {
        Assert.hasText(username, "Username must not be empty");
        Assert.hasText(password, "Password must not be empty");

        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;

        UsersResource usersResource = users(keycloak, realm);

        Optional<UserRepresentation> existingUser =
                usersResource.search(username, 0, 1)
                        .stream()
                        .filter(u -> u.getUsername().equalsIgnoreCase(username))
                        .findFirst();

        if (existingUser.isPresent()) {
            return existingUser.get().getId();
        }

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

        try (Response response = usersResource.create(user)) {

            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                return usersResource.search(username, 0, 1)
                        .stream()
                        .filter(u -> u.getUsername().equalsIgnoreCase(username))
                        .findFirst()
                        .map(UserRepresentation::getId)
                        .orElseThrow(() ->
                                new IllegalStateException("User exists but cannot retrieve ID")
                        );
            }

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

    public void enableUser(String realm, String userId) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        UserRepresentation rep = user(keycloak, realm, userId).toRepresentation();
        rep.setEnabled(true);
        user(keycloak, realm, userId).update(rep);
    }

    public void disableUser(String realm, String userId) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        UserRepresentation rep = user(keycloak, realm, userId).toRepresentation();
        rep.setEnabled(false);
        user(keycloak, realm, userId).update(rep);
    }

    public void deleteUser(String realm, String userId) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        users(keycloak, realm).delete(userId);
    }

    public Optional<UserRepresentation> findByUsername(String realm, String username) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        return users(keycloak, realm).search(username, 0, 1).stream().findFirst();
    }

    public Optional<UserRepresentation> findById(String realm, String userId) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        try {
            return Optional.of(user(keycloak, realm, userId).toRepresentation());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /* ============================================================
       Password & Email Actions
       ============================================================ */

    public void sendPasswordResetEmail(String realm, String userId) {
        Assert.hasText(userId, "userId must not be empty");
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        user(keycloak, realm, userId).executeActionsEmail(List.of("UPDATE_PASSWORD"));
    }

    public void sendVerifyEmail(String realm, String userId) {
        Assert.hasText(userId, "userId must not be empty");
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        user(keycloak, realm, userId).executeActionsEmail(List.of("VERIFY_EMAIL"));
    }

    public void sendVerifyAndResetPasswordEmail(String realm, String userId) {
        Assert.hasText(userId, "userId must not be empty");
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        user(keycloak, realm, userId).executeActionsEmail(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
    }

    public void resetPassword(String realm, String userId, String newPassword, boolean temporary) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(temporary);
        user(keycloak, realm, userId).resetPassword(credential);
    }

    /* ============================================================
       Role Management
       ============================================================ */

    public void assignRealmRole(String realm, String userId, String roleName) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        RoleRepresentation role = roles(keycloak, realm).get(roleName).toRepresentation();
        user(keycloak, realm, userId).roles().realmLevel().add(List.of(role));
    }

    public void removeRealmRole(String realm, String userId, String roleName) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        RoleRepresentation role = roles(keycloak, realm).get(roleName).toRepresentation();
        user(keycloak, realm, userId).roles().realmLevel().remove(List.of(role));
    }

    public List<String> getUserRealmRoles(String realm, String userId) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        return user(keycloak, realm, userId)
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

    public void addUserToGroup(String realm, String userId, String groupName) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        GroupRepresentation group = groups(keycloak, realm).groups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Group not found: " + groupName)
                );
        user(keycloak, realm, userId).joinGroup(group.getId());
    }

    public void removeUserFromGroup(String realm, String userId, String groupName) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        GroupRepresentation group = groups(keycloak, realm).groups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Group not found: " + groupName)
                );
        user(keycloak, realm, userId).leaveGroup(group.getId());
    }

    public List<String> getUserGroups(String realm, String userId) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        return user(keycloak, realm, userId)
                .groups()
                .stream()
                .map(GroupRepresentation::getName)
                .collect(Collectors.toList());
    }

    /* ============================================================
       Listing Users
       ============================================================ */

    public List<UserRepresentation> listUsers(String realm, int firstResult, int maxResults) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        return users(keycloak, realm).list(firstResult, maxResults);
    }

    public List<UserRepresentation> listAllUsers(String realm) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        List<UserRepresentation> allUsers = new ArrayList<>();
        int first = 0;

        while (true) {
            List<UserRepresentation> batch = users(keycloak, realm).list(first, DEFAULT_PAGE_SIZE);

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
            String realm,
            String searchQuery,
            int firstResult,
            int maxResults
    ) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        return users(keycloak, realm).search(searchQuery, firstResult, maxResults);
    }

    public List<UserRepresentation> searchUsersByAttribute(
            String realm,
            String attributeKey,
            String attributeValue
    ) {
        Assert.hasText(attributeKey, "Attribute key must not be empty");
        Assert.hasText(attributeValue, "Attribute value must not be empty");
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        return users(keycloak, realm).searchByAttributes(attributeKey + ":" + attributeValue);
    }

    public List<UserRepresentation> searchUsersByAttributes(
            String realm,
            Map<String, String> attributes
    ) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyList();
        }

        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;

        String query = attributes.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        return users(keycloak, realm).searchByAttributes(query);
    }
    /* ============================================================
   Group Listing
   ============================================================ */

    public List<GroupRepresentation> getAllGroups(String realm) {
        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        return groups(keycloak, realm).groups();
    }

/* ============================================================
   Group Creation
   ============================================================ */

    public String createGroup(String realm, String groupName, String parentGroupId) {
        Assert.hasText(groupName, "Group name must not be empty");

        Keycloak keycloak = realm.equals(realmName) ? adminKeycloak : baseKeycloak;
        GroupsResource groupsResource = groups(keycloak, realm);

        GroupRepresentation group = new GroupRepresentation();
        group.setName(groupName);

        try (Response response = parentGroupId != null
                ? groupsResource.group(parentGroupId).subGroup(group)
                : groupsResource.add(group)) {

            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new IllegalStateException(
                        "Failed to create group. Status: "
                                + response.getStatus()
                                + " - "
                                + response.getStatusInfo().getReasonPhrase()
                );
            }

            return extractGroupId(response.getLocation());
        }
    }

    private String extractGroupId(URI location) {
        if (location == null) {
            throw new IllegalStateException("Group created but no Location header returned.");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

}
