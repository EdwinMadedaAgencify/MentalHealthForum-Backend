package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service contract for managing users, including interaction with Keycloak (for identity)
 * and the local database (for user profile data).
 * All methods now return Mono for non-blocking execution.
 */
public interface UserService {

    /**
     * Registers a new user.
     * Errors (UserExistsException, InvalidPasswordException, PasswordMismatchException)
     * are signaled via Mono.error().
     * @param registerUserRequest The registration details.
     * @return Mono of the created user's ID.
     */
    Mono<String> registerUser(RegisterUserRequest registerUserRequest);

    /**
     * Retrieves a user's details by their ID.
     * Error (UserDoesNotExistException) is signaled via Mono.error().
     *
     * @param userId The ID of the user (Keycloak ID or internal ID, depending on implementation).
     * @return Mono of the KeycloakUserDto.
     */
    Mono<KeycloakUserDto> getUser(String userId);

    /**
     * Retrieves a paginated list of all users.
     * @return Mono of KeycloakUserDtos.
     */
    Mono<List<KeycloakUserDto>> getAllUsers();

    /**
     * Updates an existing user's profile.
     * Errors (UserExistsException, UserDoesNotExistException) are signaled via Mono.error().
     *
     * @param userId                   The ID of the user to update.
     * @param updateUserProfileRequest The update details.
     * @return Mono of the updated KeycloakUserDto.
     */
    Mono<KeycloakUserDto> updateUserProfile(String userId, UpdateUserProfileRequest updateUserProfileRequest);


    /**
     * Resets a user's password.
     * Errors (PasswordMismatchException, InvalidPasswordException, UserDoesNotExistException)
     * are signaled via Mono.error().
     * @param userId The ID of the user.
     * @param resetPasswordRequest The password reset details.
     * @return Mono<Void> signaling completion.
     */
    Mono<Void> resetPassword(String userId, ResetPasswordRequest resetPasswordRequest);

    /**
     * Deletes a user.
     * Error (UserDoesNotExistException) is signaled via Mono.error().
     * @param userId The ID of the user to delete.
     * @return Mono<Void> signaling completion.
     */
    Mono<Void> deleteUser(String userId);
}