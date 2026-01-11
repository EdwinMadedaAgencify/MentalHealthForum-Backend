package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.enums.InternalRole;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RealmRole;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPasswordException;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Optional;

/**
 * Interface defining the contract for all blocking Keycloak Admin Client interactions.
 * This contract enforces the abstraction layer, ensuring the reactive service layer
 * deals with our internal DTOs (KeycloakUserDto) rather than raw Keycloak types.
 */
public interface KeycloakAdminManager {

    // --- AppUser Lookup Operations (Returning DTOs) ---

    Optional<UserRepresentation> findUserByUserId(String userId);

    Optional<UserRepresentation> findUserByUsername(String username);

    Optional<UserRepresentation> findUserByEmail(String email);

    Optional<UserRepresentation> findUserByIdentifier(String identifier);

    long countUsers();

    List<UserRepresentation> listUsers(int firstResult, int size);

    List<UserRepresentation> listAllUsers();

    // --- AppUser Management Operations ---

    String createUser(UserRepresentation user);

    void updateUser(UserRepresentation userRep);

    void deleteUser(String userId);

    void resetPassword(String userId, String newPassword) throws InvalidPasswordException;

    // --- Credential and Role Helpers ---

    // --- Role, Groups and Credential Helpers (Now managed here) ---
    List<String> getUserRealmRolesFromGroups(String userId);

    void assignUserRole(String userId, RealmRole role);

    List<String> getUserRealmRoles(String userId);

    void assignInternalRole(String userId, InternalRole internalRole);

    void removeInternalRole(String userId, InternalRole internalRole);

    void assignUserToGroup(String userId, GroupPath group);

    String getUserPrimaryGroupPath(String userId);

    List<String> getUserGroups(String userId);

    Optional<List<String>> getRequiredActions(String identifier);

    void setUserAttribute(String userId, String key, String value);

    Optional<String> getUserAttribute(String userId, String key);

    void markAsSyncedLocally(String userId, boolean sync);

    boolean isSyncedLocally(String userId);

    List<UserRepresentation> findUnsyncedUsers();

    void verifyUserEmail(String email);

    CredentialRepresentation createPasswordCredential(String password) throws InvalidPasswordException;
}