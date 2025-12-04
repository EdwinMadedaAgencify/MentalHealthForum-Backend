package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RealmRole;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.PasswordMismatchException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserExistsException;
import com.mentalhealthforum.mentalhealthforum_backend.service.KeycloakAdminManager;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserService;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.mentalhealthforum.mentalhealthforum_backend.utils.ChangeUtils.setIfChangedStrict;


@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final RealmRole DEFAULT_FORUM_ROLE = RealmRole.FORUM_MEMBER;

    private final KeycloakAdminManager adminManager;

    // Inject the new KeycloakAdminManagerImpl
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
    public Mono<KeycloakUserDto> updateUserProfile(String userId, UpdateUserProfileRequest updateUserProfileRequest){
        return Mono.fromCallable(() -> {
                    // Fetch user from Keycloak
                    UserRepresentation userRep = adminManager.findUserByUserId(userId)
                            .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));

                    boolean keycloakNeedsUpdate = false;

                    keycloakNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.firstName(), userRep.getFirstName(), userRep::setFirstName);
                    keycloakNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.lastName(), userRep.getLastName(), userRep::setLastName);

                    String newEmail = updateUserProfileRequest.email() != null ? updateUserProfileRequest.email().trim().toLowerCase() : null;
                    Optional<UserRepresentation> existingUserWithNewEmail = adminManager.findUserByEmail(newEmail);
                    if (existingUserWithNewEmail.isPresent() && !existingUserWithNewEmail.get().getId().equals(userId)) {
                        throw new UserExistsException("The new email address is already in use by another account.");
                    }

                    keycloakNeedsUpdate |= setIfChangedStrict(newEmail, userRep.getEmail(), userRep::setEmail);

                    // Perform update only if any changes detected
                    if (keycloakNeedsUpdate) {
                        adminManager.updateUser(userRep); // blocking Keycloak update
                        log.info("Successfully updated profile for user ID: {}", userId);
                    } else {
                        log.debug("No profile changes detected for user ID: {}", userId);
                    }

                    return mapToKeycloakUserDto(userRep);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        return Mono.fromRunnable(() -> {
                    // Blocking lookup
                    adminManager.findUserByUserId(userId)
                            .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId));

                    // Block deletion in Keycloak
                    adminManager.deleteUser(userId);

                    log.info("Successfully deleted user with ID: {}", userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<KeycloakUserDto> getUser(String userId) {
        return Mono.fromCallable(() ->
                        // Blocking lookup, then map to Response DTO
                        adminManager.findUserByUserId(userId)
                                .map(this::mapToKeycloakUserDto)
                                .orElseThrow(() -> new UserDoesNotExistException("User not found for ID: " + userId))
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<KeycloakUserDto>> getAllUsers() {
        return Mono.fromCallable(() -> {
                    // Blocking call to list all users (no pagination)
                    List<UserRepresentation> userReps = adminManager.listAllUsers(); // Assuming there's a method to list all users

                    // Map the list of UserRepresentation to KeycloakUserDto DTOs
                    return userReps.stream()
                            .map(this::mapToKeycloakUserDto)
                            .toList();
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

    private KeycloakUserDto mapToKeycloakUserDto(UserRepresentation userRep){
        return new KeycloakUserDto(
                userRep.getId(),
                userRep.getUsername(),
                userRep.getFirstName(),
                userRep.getLastName(),
                userRep.getEmail(),
                userRep.isEnabled(),
                userRep.isEmailVerified(),
                userRep.getCreatedTimestamp()
        );
    }
}