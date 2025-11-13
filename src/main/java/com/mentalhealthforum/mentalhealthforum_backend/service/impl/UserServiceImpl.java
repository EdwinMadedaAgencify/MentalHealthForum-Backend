package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.RegisterUserRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ResetPasswordRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.UpdateUserProfileRequest;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ForumRole;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPasswordException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.PasswordMismatchException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserExistsException;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final KeycloakProperties keycloakProperties;
    private Keycloak keycloak;

    private static final String DEFAULT_FORUM_ROLE = ForumRole.FORUM_MEMBER.getRoleName();
    private static final String PASSWORD_POLICY_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9\\s]).{8,}$";

    public UserServiceImpl(KeycloakProperties keycloakProperties) {
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
        log.info("Keycloak Admin Client initialized using Client Credentials.");
    }

    // ------------------ Public API Methods ------------------

    @Override
    public String registerUser(RegisterUserRequest registerUserRequest)
            throws UserExistsException, InvalidPasswordException, PasswordMismatchException {

        String username = registerUserRequest.username().trim();
        String email = registerUserRequest.email().trim().toLowerCase();
        String firstName = registerUserRequest.firstName().trim();
        String lastName = registerUserRequest.lastName().trim();
        String password = registerUserRequest.password().trim();
        String confirmPassword = registerUserRequest.confirmPassword().trim();

        if (findUserByUsername(username).isPresent()) {
            throw new UserExistsException("An account already exists for this username.");
        }

        if (findUserByEmail(email).isPresent()) {
            throw new UserExistsException("An account already exists for this email.");
        }

        if (!password.equals(confirmPassword)) {
            throw new PasswordMismatchException("Password and confirmation password do not match.");
        }

        CredentialRepresentation passwordCred = createPasswordCredential(password);

        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setCredentials(Collections.singletonList(passwordCred));

        // Temporary FIX: Set email verified to true to allow ROPC flow
        user.setEmailVerified(true);

        // Temporary FIX: Clear any default required actions (e.g., VERIFY_EMAIL)
        user.setRequiredActions(Collections.emptyList());

        String userId;
        try (Response response = getUsersResource().create(user)) {
            if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new UserExistsException("An account already exists.");
            }

            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                String errorDetail = response.readEntity(String.class);
                log.error("Keycloak user creation failed with status {}: {}", response.getStatus(), errorDetail);
                throw new RuntimeException("Could not complete user registration due to an internal server error.");
            }

            userId = CreatedResponseUtil.getCreatedId(response);
        } catch (BadRequestException e) {
            log.error("Keycloak user creation failed with a BadRequest.", e);
            throw new RuntimeException("Could not create user due to a policy violation.", e);
        } catch (RuntimeException e) {
            log.error("Error during Keycloak user creation.", e);
            throw new RuntimeException("Could not create user in Keycloak.", e);
        }

        assignUserRole(userId, DEFAULT_FORUM_ROLE);
        return userId;
    }

    @Override
    public UserRepresentation updateUserProfile(String userId, UpdateUserProfileRequest updateUserProfileRequest)
            throws UserDoesNotExistException, UserExistsException {

        UserRepresentation userRep = findUserByUserId(userId)
                .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));

        boolean isUpdated = false;

        isUpdated |= setIfChanged(updateUserProfileRequest.firstName(), userRep.getFirstName(), userRep::setFirstName);
        isUpdated |= setIfChanged(updateUserProfileRequest.lastName(), userRep.getLastName(), userRep::setLastName);

        String newEmail = updateUserProfileRequest.email() != null ? updateUserProfileRequest.email().trim().toLowerCase() : null;
        Optional<UserRepresentation> existingUserWithNewEmail = findUserByEmail(newEmail);
        if (existingUserWithNewEmail.isPresent() && !existingUserWithNewEmail.get().getId().equals(userId)) {
            throw new UserExistsException("The new email address is already in use by another account.");
        }

        isUpdated |= setIfChanged(newEmail, userRep.getEmail(), userRep::setEmail);

        if (isUpdated) {
            getUsersResource().get(userId).update(userRep);
            log.info("Successfully updated profile for user ID: {}", userId);
        } else {
            log.debug("No profile changes detected for user ID: {}", userId);
        }

        return userRep;
    }

    @Override
    public void deleteUser(String userId) throws UserDoesNotExistException {
        findUserByUserId(userId)
                .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));

        getUsersResource().get(userId).remove();
        log.info("Successfully deleted user with ID: {}", userId);
    }

    @Override
    public UserRepresentation getUser(String userId) throws UserDoesNotExistException {
        return findUserByUserId(userId)
                .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));
    }

    @Override
    public PaginatedResponse<UserRepresentation> getAllUsers(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new BadRequestException("Invalid pagination parameters: page >= 0 and size > 0 required.");
        }

        int firstResult = page * size;
        List<UserRepresentation> content = getUsersResource().list(firstResult, size);
        long totalElements = (long) getUsersResource().count();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean isLastPage = page >= totalPages - 1;

        return new PaginatedResponse<>(content, page, size, totalElements, totalPages, isLastPage);
    }

    @Override
    public void resetPassword(String userId, ResetPasswordRequest resetPasswordRequest)
            throws PasswordMismatchException, InvalidPasswordException, UserDoesNotExistException {

        findUserByUserId(userId)
                .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));

        if (!resetPasswordRequest.newPassword().equals(resetPasswordRequest.confirmPassword())) {
            throw new PasswordMismatchException("New password and confirmation do not match.");
        }

        CredentialRepresentation passwordCred = createPasswordCredential(resetPasswordRequest.newPassword().trim());
        UserResource userResource = getUsersResource().get(userId);

        try {
            userResource.resetPassword(passwordCred);
            log.info("Successfully reset password for user ID: {}", userId);
        } catch (BadRequestException e) {
            log.warn("Keycloak password validation failed (400). Returning InvalidPassword error.", e);
            throw new InvalidPasswordException(
                    "The new password violates a security policy or lacks required complexity. Please check the rules and try again.", e
            );
        } catch (RuntimeException e) {
            log.error("Password reset failed due to an unexpected server error.", e);
            throw new InvalidPasswordException("Password reset failed due to an unexpected server error.");
        }
    }

    // ------------------ User Lookup Methods ------------------

    public Optional<UserRepresentation> findUserByUserId(String userId) {
        try {
            return Optional.of(getUsersResource().get(userId).toRepresentation());
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Optional.empty();
        }
    }

    public Optional<UserRepresentation> findUserByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return getUsersResource().searchByUsername(username.trim(), true).stream().findFirst();
    }

    public Optional<UserRepresentation> findUserByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return getUsersResource().searchByEmail(email.trim(), true).stream().findFirst();
    }

    // ------------------ Private Helper Methods ------------------

    private RealmResource getRealmResource() {
        return keycloak.realm(keycloakProperties.getRealm());
    }

    private UsersResource getUsersResource() {
        return getRealmResource().users();
    }

    private boolean setIfChanged(String newValue, String currentValue, Consumer<String> setter) {
        if (newValue != null && !newValue.isBlank() && !newValue.equals(currentValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }

    private CredentialRepresentation createPasswordCredential(String password) throws InvalidPasswordException {
        if (password == null || password.length() < 8) {
            throw new InvalidPasswordException("Password must be at least 8 characters long.");
        }

        if (!password.matches(PASSWORD_POLICY_REGEX)) {
            throw new InvalidPasswordException(
                    "Password must contain at least 8 characters, including 1 digit, 1 uppercase letter, 1 lowercase letter, and 1 special character."
            );
        }

        CredentialRepresentation passwordCred = new CredentialRepresentation();
        passwordCred.setTemporary(false);
        passwordCred.setType(CredentialRepresentation.PASSWORD);
        passwordCred.setValue(password);
        return passwordCred;
    }

    private void assignUserRole(String userId, String roleName) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            var roleRep = getRealmResource().roles().get(roleName).toRepresentation();
            userResource.roles().realmLevel().add(Collections.singletonList(roleRep));
            log.info("Assigned role {} to user {}", roleName, userId);
        } catch (Exception e) {
            log.error("Failed to assign role {} to user {}", roleName, userId, e);
            throw new RuntimeException("Failed to assign default role.", e);
        }
    }
}
