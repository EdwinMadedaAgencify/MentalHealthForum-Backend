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
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;


@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final KeycloakAdminManager adminManager;

    private static final ForumRole DEFAULT_FORUM_ROLE = ForumRole.FORUM_MEMBER;

    // Inject the new KeycloakAdminManager
    public UserServiceImpl(KeycloakAdminManager adminManager) {
        this.adminManager = adminManager;
    }

    // ------------------ Public API Methods (Reactive Wrappers) ------------------

    @Override
    public Mono<String> registerUser(RegisterUserRequest registerUserRequest) {
        // Run blocking Keycloak Admin Client logic on a dedicated thread pool
        return Mono.fromCallable(() -> {
                    String username = registerUserRequest.username().trim();
                    String email = registerUserRequest.email().trim().toLowerCase();

                    // Blocking checks delegated to the Manager
                    if (adminManager.findUserByUsername(username).isPresent()) {
                        throw new UserExistsException("An account already exists for this username.");
                    }

                    if (adminManager.findUserByEmail(email).isPresent()) {
                        throw new UserExistsException("An account already exists for this email.");
                    }

                    if (!registerUserRequest.password().trim().equals(registerUserRequest.confirmPassword().trim())) {
                        throw new PasswordMismatchException("Password and confirmation password do not match.");
                    }

                    // Create password credential using the Manager's method
                    // This method is already validated to throw InvalidPasswordException
                    String password = registerUserRequest.password().trim();
                    var passwordCred = adminManager.createPasswordCredential(password);

                    UserRepresentation user = new UserRepresentation();
                    user.setEnabled(true);
                    user.setUsername(username);
                    user.setEmail(email);
                    user.setFirstName(registerUserRequest.firstName().trim());
                    user.setLastName(registerUserRequest.lastName().trim());
                    user.setCredentials(Collections.singletonList(passwordCred));

                    // Temporary FIX: Set email verified to true & clear required actions for ROPC flow
                    user.setEmailVerified(true);
                    user.setRequiredActions(Collections.emptyList());

                    // Blocking user creation delegated to the Manager
                    String userId = adminManager.createUser(user);

                    // Blocking role assignment delegated to the Manager
                    adminManager.assignUserRole(userId, DEFAULT_FORUM_ROLE);

                    return userId;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UserRepresentation> updateUserProfile(String userId, UpdateUserProfileRequest updateUserProfileRequest) {
        return Mono.fromCallable(() -> {
                    UserRepresentation userRep = adminManager.findUserByUserId(userId)
                            .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));

                    boolean isUpdated = false;

                    isUpdated |= setIfChanged(updateUserProfileRequest.firstName(), userRep.getFirstName(), userRep::setFirstName);
                    isUpdated |= setIfChanged(updateUserProfileRequest.lastName(), userRep.getLastName(), userRep::setLastName);

                    String newEmail = updateUserProfileRequest.email() != null ? updateUserProfileRequest.email().trim().toLowerCase() : null;
                    Optional<UserRepresentation> existingUserWithNewEmail = adminManager.findUserByEmail(newEmail);

                    if (existingUserWithNewEmail.isPresent() && !existingUserWithNewEmail.get().getId().equals(userId)) {
                        throw new UserExistsException("The new email address is already in use by another account.");
                    }

                    isUpdated |= setIfChanged(newEmail, userRep.getEmail(), userRep::setEmail);

                    if (isUpdated) {
                        adminManager.updateUser(userRep); // Blocking update
                        log.info("Successfully updated profile for user ID: {}", userId);
                    } else {
                        log.debug("No profile changes detected for user ID: {}", userId);
                    }

                    return userRep;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        return Mono.fromRunnable(() -> {
                    // Blocking lookup
                    adminManager.findUserByUserId(userId)
                            .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));

                    adminManager.deleteUser(userId); // Blocking deletion
                    log.info("Successfully deleted user with ID: {}", userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<UserRepresentation> getUser(String userId) {
        return Mono.fromCallable(() ->
                        adminManager.findUserByUserId(userId) // Blocking lookup
                                .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId))
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<PaginatedResponse<UserRepresentation>> getAllUsers(int page, int size) {
        return Mono.fromCallable(() -> {
                    if (page < 0 || size <= 0) {
                        throw new RuntimeException("Invalid pagination parameters: page >= 0 and size > 0 required.");
                    }

                    int firstResult = page * size;
                    var content = adminManager.listUsers(firstResult, size); // Blocking list call
                    long totalElements = adminManager.countUsers(); // Blocking count call
                    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
                    boolean isLastPage = page >= totalPages - 1;

                    return new PaginatedResponse<>(content, page, size, totalElements, totalPages, isLastPage);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> resetPassword(String userId, ResetPasswordRequest resetPasswordRequest) {
        return Mono.fromRunnable(() -> {
                    adminManager.findUserByUserId(userId) // Blocking lookup
                            .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));

                    if (!resetPasswordRequest.newPassword().equals(resetPasswordRequest.confirmPassword())) {
                        throw new PasswordMismatchException("New password and confirmation do not match.");
                    }

                    adminManager.resetPassword(userId, resetPasswordRequest.newPassword().trim()); // Blocking reset
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    // ------------------ Private Helper Methods (Simplified) ------------------

    private boolean setIfChanged(String newValue, String currentValue, Consumer<String> setter) {
        if (newValue != null && !newValue.isBlank() && !newValue.equals(currentValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }
}