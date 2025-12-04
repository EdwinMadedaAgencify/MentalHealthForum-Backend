package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.KeycloakSyncException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.mapper.UserResponseMapper;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import com.mentalhealthforum.mentalhealthforum_backend.repository.AppUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.KeycloakAdminManager;
import com.mentalhealthforum.mentalhealthforum_backend.utils.PaginationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static com.mentalhealthforum.mentalhealthforum_backend.utils.ChangeUtils.*;

/**
 * Handles part of the user-related business logic, including profile synchronization
 * with Keycloak and data augmentation (like setting the 'isSelf' flag).
 */
@Service
public class AppUserServiceImpl implements AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserServiceImpl.class);

    private final AppUserRepository appUserRepository;
    private final KeycloakAdminManager adminManager;
    private final UserResponseMapper userResponseMapper;
    private final WebClient webClient;
    private final  String userInfoUri;

    public AppUserServiceImpl(
            AppUserRepository appUserRepository,
            WebClient.Builder webClientBuilder,
            KeycloakProperties keycloakProperties, KeycloakAdminManager adminManager, UserResponseMapper userResponseMapper) {
        this.appUserRepository = appUserRepository;
        this.adminManager = adminManager;
        this.userResponseMapper = userResponseMapper;

        String authServerUrl = keycloakProperties.getAuthServerUrl();
        String realm = keycloakProperties.getRealm();

        this.userInfoUri = String.format("/realms/%s/protocol/openid-connect/userinfo", realm);
        this.webClient = webClientBuilder.baseUrl(authServerUrl).build();
    }

    /**
     * Creates the initial R2DBC profile immediately after Keycloak registration.
     * This is the backend's orchestration point for new users.
     */
    public Mono<UserResponse> syncUserViaAdminClient(KeycloakUserDto keycloakUserDto){
        AppUser userDetails = new AppUser(
                keycloakUserDto.userId(),
                keycloakUserDto.email(),
                keycloakUserDto.username(),
                keycloakUserDto.firstName(),
                keycloakUserDto.lastName());

        // Set fields that are only relevant on INSERT
        userDetails.setIsEnabled(keycloakUserDto.enabled());
        userDetails.setDateJoined(keycloakUserDto.getCreatedInstant());

        // Fetch roles and groups from Keycloak
        List<String> roles = adminManager.getUserRealmRoles(keycloakUserDto.userId());
        List<String> groups = adminManager.getUserGroups(keycloakUserDto.userId());

        // Convert to sets for local storage
        userDetails.setRoles(new HashSet<>(roles));
        userDetails.setGroups(new HashSet<>(groups));
        userDetails.setLastSyncedAt(Instant.now());

        return appUserRepository.findAppUserByKeycloakId(String.valueOf(userDetails.getKeycloakId()))
                .flatMap(existingUser -> {
                    // --- Incrementally sync Keycloak-authoritative fields ---
                    boolean localNeedsUpdate = false;

                    // Incremental sync for Keycloak-authoritative fields
                    localNeedsUpdate |= setIfChanged(userDetails.getEmail(), existingUser.getEmail(), existingUser::setEmail);
                    localNeedsUpdate |= setIfChanged(userDetails.getUsername(), existingUser.getUsername(), existingUser::setUsername);
                    localNeedsUpdate |= setIfChanged(userDetails.getFirstName(), existingUser.getFirstName(), existingUser::setFirstName);
                    localNeedsUpdate |= setIfChanged(userDetails.getLastName(), existingUser.getLastName(), existingUser::setLastName);

                    // Incremental sync for status & cached fields
                    localNeedsUpdate |= setIfChanged(userDetails.getIsEnabled(), existingUser.getIsEnabled(), existingUser::setIsEnabled);
                    localNeedsUpdate |= setIfChanged(userDetails.getRoles(), existingUser.getRoles(), existingUser::setRoles);
                    localNeedsUpdate |= setIfChanged(userDetails.getGroups(), existingUser.getGroups(), existingUser::setGroups);

                    // R2DBC performs an UPDATE here.
                    return localNeedsUpdate ? appUserRepository.save(existingUser) : Mono.just(existingUser);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    //  User does NOT exist: Insert the new user
                    // R2DBC performs an INSERT here.
                    userDetails.setBio(generateDefaultBio(keycloakUserDto.firstName(), keycloakUserDto.lastName()));
                    return appUserRepository.save(userDetails);
                }))
                .map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, keycloakUserDto.userId()));
    }
    /**
     * @deprecated replaced by {@link #syncUserViaAdminClient(KeycloakUserDto)}
     * due to limitations in token-based synchronization. The userinfo endpoint lacks critical
     * metadata (createdTimestamp, enabled status) and is restricted to current user only.
     *
     * <p>For user synchronization, use:
     * <ul>
     *   <li>{@link #syncUserViaAdminClient(KeycloakUserDto)} - Complete user data sync</li>
     *   <li>{@link #getAppUserWithSelfFlag(String, String)} - User profile retrieval</li>
     *   <li>{@link #updateLocalProfile(String, UpdateUserProfileRequest)} - Profile updates</li>
     * </ul>
     *
     * @param accessToken The JWT access token (user-specific)
     * @return Mono<UserResponse> User profile data
     * @throws KeycloakSyncException If synchronization fails
     */
    @Deprecated
    public Mono<UserResponse> syncUserViaAPI(String accessToken){
        return getKeycloakUserInfo(accessToken)
                .flatMap(this::findOrCreateUser)
                .map(userResponseMapper::toSelfResponse)
                .onErrorMap(e -> {
                    // Log the technical details of the error internally, but throw a generic exception for the user
                    log.error("Error syncing with Keycloak: {}", e.getMessage(), e);
                    return new KeycloakSyncException("An error occured while syncing user data.", e);
                });
    }
    /**
     * Step 1: Calls Keycloak's userinfo endpoint to get the authoritative profile.
     */
    private Mono<UserInfoDto> getKeycloakUserInfo(String accessToken) {
        return webClient.get()
                .uri(userInfoUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    return response.bodyToMono(String.class)
                            .doOnNext(body -> log.warn("Keycloak Profile Sync Failed ({}): {}", response.statusCode(), body))
                            .flatMap(body -> {
                                return Mono.error(new KeycloakSyncException(
                                        "Profile Sync failed." + body
                                ));
                            });
                })
                .bodyToMono(UserInfoDto.class)
                .doOnNext(userInfo -> log.info("Keycloak user info received: {}", userInfo))
                .doOnError(e -> log.error("Error fetching keycloak user info: {}", e.getMessage(), e));
    }
    /**
     * Step 2: Uses the authoritative DTO to find the user in the database or create a new one.
     */
    private Mono<AppUser> findOrCreateUser(UserInfoDto userInfoDto) {
        // Look up the user by the Keycloak ID (sub)
        return appUserRepository.findAppUserByKeycloakId(userInfoDto.keycloakId())
                .flatMap(existingUser -> {
                    // Update: Sync mutable fields from Keycloak
                    existingUser.setEmail(userInfoDto.email());
                    existingUser.setUsername(userInfoDto.preferredUsername());
                    existingUser.setFirstName(userInfoDto.givenName());
                    existingUser.setLastName(userInfoDto.familyName());
                    return appUserRepository.save(existingUser);

                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Create: User is logging in for the first time
                    AppUser newAppUser = new AppUser(
                            userInfoDto.keycloakId(),
                            userInfoDto.email(),
                            userInfoDto.preferredUsername(),
                            userInfoDto.givenName(),
                            userInfoDto.familyName()
                    );
                    newAppUser.setBio(generateDefaultBio(userInfoDto.givenName(), userInfoDto.familyName()));
                    return appUserRepository.save(newAppUser);
                }));

    }
    /**
     * Fetches a single user profile from the local database, or throws if not found.
     * Sets 'self' flag if the current user matches the one being requested.
     *
     * @param userId The Keycloak ID of the profile to fetch.
     * @param currentUserId The Keycloak ID of the authenticated user.
     */
    public Mono<UserResponse> getAppUserWithSelfFlag(String userId, String currentUserId) {
        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new UserDoesNotExistException("User not found for ID: " + userId)))
                .map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, currentUserId));
    }
    /**
     * Fetches all users, applies the 'isSelf' flag, and paginates the result.
     * @param currentUserId The Keycloak ID of the authenticated user.
     * @param page The requested page number (0-indexed).
     * @param size The number of items per page.
     * @return Mono<PaginatedResponse<AppUser>> A paginated structure containing the users.
     */
    public Mono<PaginatedResponse<UserResponse>> getAllAppUsersWithSelfFlag(String currentUserId, int page, int size){
        // Fetch all users and set the 'self' flag on the current user
        Flux<AppUser> appUserFlux = appUserRepository.findAll();

        // Apply the self flag before passing the Flux to the pagination utility
        Flux<UserResponse> userResFlux = appUserFlux.map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, currentUserId));

        return PaginationUtils.paginate(page, size, userResFlux);
    }
    /**
     * Updates locally managed profile fields (e.g., bio).
     * @param userId The Keycloak ID of the user to update.
     * @param updateUserProfileRequest DTO containing the fields to update.
     */
    public Mono<UserResponse> updateLocalProfile(String userId, UpdateUserProfileRequest updateUserProfileRequest){
        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new UserDoesNotExistException("User not found for ID: " + userId)))
                .flatMap(appUser -> {

                    boolean localNeedsUpdate = false;

                    // --- Keycloak-authoritative fields ---
                    localNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.email(), appUser.getEmail(), appUser::setEmail);
                    localNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.firstName(), appUser.getFirstName(), appUser::setFirstName);
                    localNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.lastName(), appUser.getLastName(), appUser::setLastName);

                    // --- User-controlled fields (allow null/blank to clear) --
                    localNeedsUpdate |= setIfChangedAllowNull(updateUserProfileRequest.displayName(), appUser.getDisplayName(), appUser::setDisplayName);
                    localNeedsUpdate |= setIfChangedAllowNull(updateUserProfileRequest.bio(), appUser.getBio(), appUser::setBio);
                    localNeedsUpdate |= setIfChangedAllowNull(updateUserProfileRequest.avatarUrl(), appUser.getAvatarUrl(), appUser::setAvatarUrl);
                    localNeedsUpdate |= setIfChangedAllowNull(updateUserProfileRequest.timezone(), appUser.getTimezone(), appUser::setTimezone);

                    // Enum fields
                    localNeedsUpdate |= setIfChangedAllowNull(
                            updateUserProfileRequest.profileVisibility(),
                            appUser.getProfileVisibility(),
                            appUser::setProfileVisibility);

                    localNeedsUpdate |= setIfChangedAllowNull(
                            updateUserProfileRequest.supportRole(),
                            appUser.getSupportRole(),
                            appUser::setSupportRole);

                    // Notification preferences (JSON-backed object)
                    localNeedsUpdate |= setIfChangedAllowNull(
                            updateUserProfileRequest.notificationPreferences(),
                            appUser.getNotificationPreferences(),
                            appUser::setNotificationPreferences);

                    // --- Persist only if any changes ---
                    return localNeedsUpdate ? appUserRepository.save(appUser) : Mono.just(appUser);
                })
                .map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, userId));
    }
    /**
     * Deletes the local R2DBC profile for a given Keycloak ID.
     * @param keycloakId The Keycloak ID of the user whose profile is to be deleted.
     */
    public Mono<Void> deleteLocalProfile(String keycloakId){
        return appUserRepository
                .findAppUserByKeycloakId(keycloakId)
                .flatMap(appUserRepository::delete)
                .then();
    }
    /**
     * Generates the default bio-based on the user's first and last name.
     */
    private String generateDefaultBio(String firstName, String lastName) {
        if (firstName != null && lastName != null) {
            return String.format("Hi, I'm %s %s. I'm here to connect, learn and grow. Let's support each other!", firstName, lastName);
        } else {
            return "Hi, I'm here to connect, learn and grow. Let's support each other!";
        }
    }
}
