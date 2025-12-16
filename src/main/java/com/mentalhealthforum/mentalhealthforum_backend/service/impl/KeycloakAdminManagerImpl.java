package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RealmRole;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidGroupAssignmentException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPasswordException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserExistsException;
import com.mentalhealthforum.mentalhealthforum_backend.service.KeycloakAdminManager;
import com.mentalhealthforum.mentalhealthforum_backend.validation.password.PasswordPolicy;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.mentalhealthforum.mentalhealthforum_backend.utils.UUIDUtils.isUUID;

/**
 * Dedicated service for encapsulating all blocking operations related to the
 * Keycloak Admin Client (keycloak-admin-client). This class implements the
 * KeycloakAdminManager interface and handles the mapping from Keycloak's
 * UserRepresentation to our internal KeycloakUserDto.

 * All methods in this class are blocking and must be called within
 * Mono.fromCallable().subscribeOn(Schedulers.boundedElastic())
 * from the reactive service layer.
 */
@Service
public class KeycloakAdminManagerImpl implements KeycloakAdminManager {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminManagerImpl.class);


    private final KeycloakProperties keycloakProperties;
    private Keycloak keycloak;

    private final Map<String, String> groupCache = new ConcurrentHashMap<>();

    public KeycloakAdminManagerImpl(KeycloakProperties keycloakProperties) {
        this.keycloakProperties = keycloakProperties;
    }

    @PostConstruct
    public void init() {
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakProperties.getAuthServerUrl())
                .realm(keycloakProperties.getRealm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(keycloakProperties.getResource())
                .clientSecret(keycloakProperties.getCredentials().getSecret())
                .build();
        log.info("Keycloak Admin Client initialized successfully.");
    }

    // --- AppUser Lookup Operations (Now returning KeycloakUserDto) ---

    @Override
    public Optional<UserRepresentation> findUserByUserId(String userId) {
        try {
            return Optional.of(getUsersResource().get(userId).toRepresentation());
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserRepresentation> findUserByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return getUsersResource().searchByUsername(username.trim(), true).stream().findFirst();
    }

    @Override
    public Optional<UserRepresentation> findUserByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return getUsersResource().searchByEmail(email.trim(), true).stream().findFirst();
    }

    @Override
    public Optional<UserRepresentation> findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        return (isUUID(identifier))
                    ? findUserByUserId(identifier)
                    : getUsersResource().search(identifier).stream().findFirst();
    }

    @Override
    public long countUsers() {
        return getUsersResource().count();
    }

    @Override
    public List<UserRepresentation> listUsers(int firstResult, int size) {
        return getUsersResource().list(firstResult, size);
    }

    @Override
    public List<UserRepresentation> listAllUsers() {
        // Directly return all users
        return getUsersResource().list();
    }

    // --- AppUser Management Operations ---

    /**
     * Creates a user in Keycloak and returns the newly generated ID.
     */
    @Override
    public String createUser(UserRepresentation user) {
        try (Response response = getUsersResource().create(user)) {
            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new RuntimeException("An account already exists (Conflict).");
            }
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                String errorDetail = response.readEntity(String.class);
                log.error("Keycloak user creation failed with status {}: {}", response.getStatus(), errorDetail);
                throw new RuntimeException("Keycloak user creation failed: " + response.getStatus());
            }
            return CreatedResponseUtil.getCreatedId(response);
        } catch (BadRequestException e) {
            log.error("Keycloak user creation failed with a BadRequest.", e);
            throw new RuntimeException("AppUser creation failed due to a policy violation.", e);
        }
    }

    @Override
    public void updateUser(UserRepresentation userRep) {
        getUsersResource().get(userRep.getId()).update(userRep);
    }

    @Override
    public void deleteUser(String userId) {
        getUsersResource().get(userId).remove();
    }

    @Override
    public void resetPassword(String userId, String newPassword) throws InvalidPasswordException {
        CredentialRepresentation passwordCred = createPasswordCredential(newPassword, false);
        UserResource userResource = getUsersResource().get(userId);

        try {
            userResource.resetPassword(passwordCred);
        } catch (BadRequestException e) {
            // Keycloak returns 400 for password policy violations
            log.warn("Keycloak password validation failed (400). Returning InvalidPassword error.", e);
            throw new InvalidPasswordException(
                    "The new password violates a security policy or lacks required complexity. Please check the rules and try again.", e
            );
        } catch (RuntimeException e) {
            log.error("Password reset failed due to an unexpected server error.", e);
            throw new InvalidPasswordException("Password reset failed due to an unexpected server error.");
        }
    }

    // --- Role, Groups and Credential Helpers (Now managed here) ---

    @Override
    public List<String> getUserRealmRoles(String userId){
        try {
            return getUsersResource().get(userId)
                    .roles()
                    .realmLevel()
                    .listAll()
                    .stream()
                    .map(RoleRepresentation::getName)
                    .toList();

        } catch (Exception e){
            log.error("Failed to fetch roles for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void assignUserRole(String userId, RealmRole role) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            var roleRep = getRealmResource().roles().get(role.getRoleName()).toRepresentation();
            userResource.roles().realmLevel().add(Collections.singletonList(roleRep));
            log.info("Assigned role {} to user {}", role.getRoleName(), userId);
        } catch (Exception e) {
            log.error("Failed to assign role {} to user {}", role.getRoleName(), userId, e);
            throw new RuntimeException("Failed to assign default role.", e);
        }
    }

    @Override
    public void assignUserToGroup(String userId, GroupPath group) {

        // Validate group is assignable
        if(!group.isAssignable()){
            throw new InvalidGroupAssignmentException(
                    String.format("Group %s is not assignable. Only leaf groups with role grants can be assigned.", group)
            );
        }

        try{

            String groupId = groupCache.get(group.getPath());

            if(groupId == null){
                // Cache miss: find group recursively
                List<GroupRepresentation> groups = getRealmResource().groups().groups();
                groupId = findGroupIdByPath(groups, group.getPath());

                if(groupId != null){
                    // Cache it for next time
                    groupCache.put(group.getPath(), groupId);
                }
                else {
                    throw new RuntimeException("Group not found: " + group.getPath());
                }
            }

            // Assign user to group
            getUsersResource().get(userId).joinGroup(groupId);
            log.info("Assigned user {} to group {}", userId, group.getPath());
        } catch (Exception e){
            log.error("Failed to assign user {} to group {}", userId, group.getPath(), e);
            throw new RuntimeException("Failed to assign user to group.", e);
        }
    }

    private String findGroupIdByPath(List<GroupRepresentation> topLevelGroups, String targetPath) {
        log.debug("Searching for group with path: {}", targetPath);

        // split the path into components
        String[] pathComponents = targetPath.split("/");

        // start from top-level groups
        return findGroupRecursive(topLevelGroups, pathComponents, 1); // Start at index 1 (skip empty first element)
    }

    private String findGroupRecursive(List<GroupRepresentation> currentLevelGroups, String[] pathComponents, int currentDepth) {
        if(currentDepth >= pathComponents.length){
            return null; // Reached end without finding
        }

        String currentComponent = pathComponents[currentDepth];

        for (GroupRepresentation group : currentLevelGroups) {
            if (currentComponent.equals(group.getName())) {
                // if this is the last component, return this group's ID
                if(currentDepth == pathComponents.length - 1){
                    return group.getId();
                }

                // Otherwise, search in this group's subgroups
                List<GroupRepresentation> subgroups = getRealmResource()
                        .groups()
                        .group(group.getId())
                        .getSubGroups(0, 100,true);
                    //  .getSubGroups(pathComponents[currentDepth + 1], true, 0, 1, true);

                return findGroupRecursive(subgroups, pathComponents, currentDepth + 1);

            }
        }
        return null;
    }

    @Override
    public List<String> getUserGroups(String userId){
        try {
            return getUsersResource().get(userId)
                    .groups()
                    .stream()
                    .map(GroupRepresentation::getPath)
                    .toList();

        } catch (Exception e){
            log.error("Failed to fetch groups for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<List<String>> getRequiredActions(String indentifier) {
        if(indentifier == null || indentifier.isBlank()) return Optional.empty();

        Optional<UserRepresentation> user = findUserByIdentifier(indentifier);
        if(user.isEmpty()) return Optional.empty();

        List<String> actions = user.get().getRequiredActions();
        if(actions == null) return Optional.of(Collections.emptyList());

        return Optional.of(actions);
    }

    @Override
    public CredentialRepresentation createPasswordCredential(String password, boolean isTemporary) throws InvalidPasswordException {
        if (password == null || password.length() < 8) {
            throw new InvalidPasswordException("Password must be at least 8 characters long.");
        }

        if (!password.matches(PasswordPolicy.PASSWORD_POLICY_REGEX)) {
            throw new InvalidPasswordException(
                   PasswordPolicy.MESSAGE
            );
        }

        CredentialRepresentation passwordCred = new CredentialRepresentation();
        passwordCred.setTemporary(isTemporary);
        passwordCred.setType(CredentialRepresentation.PASSWORD);
        passwordCred.setValue(password);
        return passwordCred;
    }

    // --- Internal Keycloak Resource Getters ---

    private RealmResource getRealmResource() {
        return keycloak.realm(keycloakProperties.getRealm());
    }

    private UsersResource getUsersResource() {
        return getRealmResource().users();
    }
}