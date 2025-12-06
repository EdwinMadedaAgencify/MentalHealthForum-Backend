package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InsufficientPermissionException;
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
     * @param viewerContext Context of the viewer making the request, used for privacy-aware mapping.
     *                      May be null for system operations (e.g., user registration).
     * @return Mono of the created or updated user profile response.
     */
    Mono<UserResponse> syncUserViaAdminClient(KeycloakUserDto keycloakUserDto, ViewerContext viewerContext);

    /**
     * @deprecated Use {@link #syncUserViaAdminClient(KeycloakUserDto, ViewerContext)} instead.
     * Syncs the user's profile from Keycloak to the local database using the userinfo endpoint.
     * This method is restricted to the current user only and lacks critical metadata.
     *
     * @param accessToken The JWT token used to authenticate the request to Keycloak's userinfo endpoint.
     * @return Mono of the synchronized internal application user response.
     */
    @Deprecated
    Mono<UserResponse> syncUserViaAPI(String accessToken);

    /**
     * Fetches the user's profile from the local database by their Keycloak ID.
     * Applies privacy rules based on the target user's profile visibility and the viewer's privileges.
     * Sets the 'isSelf' flag if the viewer is requesting their own profile.
     *
     * @param userId The Keycloak ID of the user whose profile is to be fetched.
     * @param viewerContext The authenticated viewer's context, used for privacy rules and 'isSelf' determination.
     * @return Mono of the user profile response, with privacy rules applied and 'isSelf' flag set.
     */
    Mono<UserResponse> getAppUserWithContext(String userId, ViewerContext viewerContext);

    /**
     * Retrieves a paginated list of all users in the system.
     * Applies privacy rules for each user based on their profile visibility and the viewer's privileges.
     * Sets the 'isSelf' flag for the current user in the list.
     *
     * @param viewerContext The authenticated viewer's context, used for privacy rules.
     * @param page The requested page number (0-indexed).
     * @param size The number of items per page.
     * @return Mono of a paginated response containing user profile data with privacy rules applied.
     */
    Mono<PaginatedResponse<UserResponse>> getAllAppUsersWithContext(ViewerContext viewerContext, int page, int size);

    /**
     * Updates the locally stored user profile (e.g., display name, bio, avatar).
     * Only allows users to update their own profiles (authorization enforced).
     * Some fields (email, first name, last name) are synchronized from Keycloak.
     *
     * @param userId The Keycloak ID of the user to update.
     * @param viewerContext The authenticated viewer's context, used for authorization.
     * @param updateUserProfileRequest DTO containing the fields to update.
     * @return Mono of the updated user profile response.
     * @throws InsufficientPermissionException if viewer is not updating their own profile
     */
    Mono<UserResponse> updateLocalProfile(String userId, ViewerContext viewerContext, UpdateUserProfileRequest updateUserProfileRequest);

    /**
     * Deletes the local user profile from the R2DBC database for a given Keycloak ID.
     * Only allows users to delete their own profiles (authorization enforced).
     * This only affects the local database record, not Keycloak.
     *
     * @param userId The Keycloak ID of the user whose local profile is to be deleted.
     * @param viewerContext The authenticated viewer's context, used for authorization.
     * @return Mono<Void> signaling the completion of the delete operation.
     * @throws InsufficientPermissionException if viewer is not deleting their own profile
     */
    Mono<Void> deleteLocalProfile(String userId, ViewerContext viewerContext);

}
