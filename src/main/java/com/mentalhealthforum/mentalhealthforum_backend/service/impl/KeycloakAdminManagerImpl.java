package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.contants.KeycloakAttributes;
import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.enums.InternalRole;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RealmRole;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RequiredAction;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidGroupAssignmentException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPasswordException;
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

import java.util.*;
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
        CredentialRepresentation passwordCred = createPasswordCredential(newPassword);
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
    public List<String> getUserRealmRolesFromGroups(String userId){
        try{
            UserResource userResource = getUsersResource().get(userId);

            // Get all groups user belongs to
            List<GroupRepresentation> userGroups = userResource.groups();

            // Collect ALL roles from ALL groups
            Set<String> rolesFromGroups = new HashSet<>();

            for (GroupRepresentation group: userGroups) {
                List<RoleRepresentation> groupRoles = getRealmResource()
                        .groups()
                        .group(group.getId())
                        .roles()
                        .realmLevel()
                        .listAll();

                rolesFromGroups.addAll(
                        groupRoles.stream()
                                .map(RoleRepresentation::getName)
                                .toList()
                );
            }

            return new ArrayList<>(rolesFromGroups);
        } catch(Exception e){
            log.error("Failed to fetch roles for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> getUserRealmRoles(String userId){
        try {
            UserResource userResource = getUsersResource().get(userId);

            Set<String> allRoles = new HashSet<>(getUserRealmRolesFromGroups(userId));

            // Get direct user roles, but EXCLUDE any that are in RealmRole enum
            // (because those should come from groups, not direct assignment)
            List<RoleRepresentation> directRoles = userResource.roles()
                    .realmLevel()
                    .listAll();

            // EXCLUDE: Remove roles that are in our RealmRole enum
            // These should only come from group inheritance
            List<String> validDirectRoles = directRoles.stream()
                    .map(RoleRepresentation::getName)
                    .filter(role -> !RealmRole.isValidRole(role))
                    .filter(role -> role.equals(InternalRole.ONBOARDING.getRoleName()))
                    .toList();

            allRoles.addAll(validDirectRoles);

            return new ArrayList<>(allRoles);

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
    public void assignInternalRole(String userId, InternalRole internalRole){
        String roleName = internalRole.getRoleName();
        String roleDescription = internalRole.getDescription();
        try {
            // Check if role exists. If not, create it.
            createRoleIfNotExists(roleName, roleDescription);

            // Get the Role Representation from the Realm
            RoleRepresentation limitedRole = getRealmResource()
                    .roles()
                    .get(roleName)
                    .toRepresentation();

            // Assign to the user
            getUsersResource().get(userId)
                    .roles()
                    .realmLevel()
                    .add(Collections.singletonList(limitedRole));
            log.info("Successfully assigned {} role to user {}", roleName, userId);
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.error("Failed to assign internal role {} to user {}", roleName, userId, e);
        }
    }

    @Override
    public void removeInternalRole(String userId, InternalRole internalRole){
        String roleName = internalRole.getRoleName();
        try {
            UserResource userResource = getUsersResource().get(userId);

            // Find the specific role among user's current realm roles
            List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();

            Optional<RoleRepresentation> limitedRole = currentRoles.stream()
                    .filter(role -> roleName.equals(role.getName()))
                    .findFirst();

            if(limitedRole.isPresent()){
                userResource.roles().realmLevel().remove(Collections.singletonList(limitedRole.get()));
                log.info("Successfully removed {} role from user {}", roleName, userId);
            }
        } catch(Exception e) {
            log.error("Failed to remove {} role from user {}", roleName, userId, e);
        }

    }

    private void createRoleIfNotExists(String roleName, String description){
        try {
            getRealmResource().roles().get(roleName).toRepresentation();
        } catch (jakarta.ws.rs.NotFoundException e){
            log.info("Role {} not found. Creating it on the fly...", roleName);
            RoleRepresentation newRole = new RoleRepresentation();
            newRole.setName(roleName);
            newRole.setDescription(description);
            getRealmResource().roles().create(newRole);

        }
    }





    @Override
    public void assignUserToGroup(String userId, GroupPath group) {

        // Validate group is assignable
        if(!group.isAssignable()){
            throw new InvalidGroupAssignmentException(group);
        }

        try{
            UserResource userResource = getUsersResource().get(userId);

            // Remove from all groups first
            List<GroupRepresentation> currentGroups = userResource.groups();
            for (GroupRepresentation currentGroup : currentGroups){
                userResource.leaveGroup(currentGroup.getId());
            }

            // Then add to new group
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
                    throw new InvalidGroupAssignmentException("Group not found: %s".formatted(group.getPath()));
                }
            }

            // Assign user to group (now they're only in this one)
            getUsersResource().get(userId).joinGroup(groupId);
            log.info("Assigned user {} to group {}", userId, group.getPath());
        } catch (Exception e){
            log.error("Failed to assign user {} to group {}", userId, group.getPath(), e);
            throw new InvalidGroupAssignmentException("Failed to assign user to group.", e);
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
    public String getUserPrimaryGroupPath(String userId){
        try{
           List<GroupRepresentation> groups = getUsersResource().get(userId).groups();
           if(groups.isEmpty()){
               // Fallback to a default if no group is found
               // though for an invited user, this shouldn't happen.
               log.warn("User {} has no groups assigned. Falling back to default.", userId);
               return GroupPath.MEMBERS_NEW.getPath();
           }

            // We return the path of the first group found.
            // In our system, the admin typically assigns exactly one.
            return groups.getFirst().getPath();
        } catch(Exception e){
            log.error("Failed to fetch primary group for user {}", userId, e);
            return GroupPath.MEMBERS_NEW.getPath();
        }
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
    public void setUserAttribute(String userId, String key, String value){
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation userRep = userResource.toRepresentation();

        Map<String, List<String>> attributes = userRep.getAttributes();
        if(attributes == null){
            attributes = new HashMap<>();
        }

        // Keycloak attributes are always Lists of Strings
        attributes.put(key, Collections.singletonList(value));
        userRep.setAttributes(attributes);

        userResource.update(userRep);
        log.info("Updated attribute '{}' to '{} for user {}'", key, value, userId);
    }


    @Override
    public Optional<String> getUserAttribute(String userId, String key){
        UserRepresentation userRep = getUsersResource().get(userId).toRepresentation();

        Map<String, List<String>> attributes = userRep.getAttributes();
        if(attributes == null){
           return Optional.empty();
        }
        List<String> values = attributes.get(key);
        return (values != null && !values.isEmpty())
                ? Optional.of(values.getFirst())
                : Optional.empty();
    }


    @Override
    public void markAsSyncedLocally(String userId, boolean sync){
        setUserAttribute(userId, KeycloakAttributes.IS_SYNCED_LOCALLY, sync? "true": "false");
    }

    @Override
    public boolean isSyncedLocally(String userId){
        return getUserAttribute(userId, KeycloakAttributes.IS_SYNCED_LOCALLY)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    @Override
    public List<UserRepresentation> findUnsyncedUsers(){
        return getUsersResource().searchByAttributes(String.format("%s:false", KeycloakAttributes.IS_SYNCED_LOCALLY));
    }

    @Override
    public void verifyUserEmail(String email) {
        findUserByEmail(email).ifPresent(userRep -> {
            userRep.setEmailVerified(true);
            // Also remove 'VERIFY_EMAIL' from required actions if it exists
            if(userRep.getRequiredActions() != null){
                userRep.getRequiredActions().remove(RequiredAction.VERIFY_EMAIL.name());
            }
            updateUser(userRep);
            log.info("Successfully marked email {} as verified in Keycloak", email);
        });
    }

    @Override
    public CredentialRepresentation createPasswordCredential(String password) throws InvalidPasswordException {
        if (password == null || password.length() < 8) {
            throw new InvalidPasswordException("Password must be at least 8 characters long.");
        }

        if (!password.matches(PasswordPolicy.PASSWORD_POLICY_REGEX)) {
            throw new InvalidPasswordException(
                   PasswordPolicy.MESSAGE
            );
        }

        CredentialRepresentation passwordCred = new CredentialRepresentation();
        passwordCred.setTemporary(false);
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