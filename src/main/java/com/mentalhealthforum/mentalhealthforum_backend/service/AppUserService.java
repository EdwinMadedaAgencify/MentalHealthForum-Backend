package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import reactor.core.publisher.Mono;

/**
 * Service contract for managing user profiles, including synchronization with Keycloak,
 * local database operations, and the handling of profile fields such as `bio` and `dateJoined`.
 * All methods return Mono for non-blocking, reactive execution.
 */
public interface AppUserService {

    /**
     * Creates the initial R2DBC profile for a user after Keycloak registration.
     * This method is called when a new user registers, either by creating a new user profile
     * or updating an existing one in the local database.
     *
     * @param keycloakUserDto The Keycloak user data transfer object containing user details.
     * @return Mono of the created or updated user profile response.
     */
    Mono<UserResponse> syncUserViaAdminClient(KeycloakUserDto keycloakUserDto);

    /**
     * Syncs the user's profile from Keycloak to the local database.
     * This is useful when an existing user logs in or when we need to refresh local profile data.
     *
     * @param accessToken The JWT token used to authenticate the request to Keycloak's userinfo endpoint.
     * @return Mono of the synchronized internal application user response.
     */
    Mono<UserResponse> syncUserViaAPI(String accessToken);

    /**
     * Fetches the user's profile from the local database by their Keycloak ID.
     * If the user profile exists, it also sets a flag (`isSelf`) to indicate if the user is the current user.
     *
     * @param userId The Keycloak ID of the user whose profile is to be fetched.
     * @param currentUserId The Keycloak ID of the authenticated user, used to set the 'isSelf' flag.
     * @return Mono of the user profile response, with the 'isSelf' flag set accordingly.
     */
    Mono<UserResponse> getAppUserWithSelfFlag(String userId, String currentUserId);

    /**
     * Retrieves a paginated list of all users in the system.
     * Applies the 'isSelf' flag for the current user in the list, useful for frontend pagination with self identification.
     *
     * @param currentUserId The Keycloak ID of the authenticated user.
     * @param page The requested page number (0-indexed).
     * @param size The number of items per page.
     * @return Mono of a paginated response containing user profile data with the 'isSelf' flag set.
     */
    Mono<PaginatedResponse<UserResponse>> getAllAppUsersWithSelfFlag(String currentUserId, int page, int size);

    /**
     * Updates the locally stored user profile (e.g., email, first name, last name, bio).
     * The update only applies to the local profile, not to Keycloak.
     *
     * @param userId The Keycloak ID of the user to update.
     * @param updateUserProfileRequest DTO containing the fields to update.
     * @return Mono of the updated user profile response.
     */
    Mono<UserResponse> updateLocalProfile(String userId, UpdateUserProfileRequest updateUserProfileRequest);

    /**
     * Deletes the local user profile from the R2DBC database for a given Keycloak ID.
     * This only affects the local database record, not Keycloak.
     *
     * @param keycloakId The Keycloak ID of the user whose local profile is to be deleted.
     * @return Mono<Void> signaling the completion of the delete operation.
     */
    Mono<Void> deleteLocalProfile(String keycloakId);

}
