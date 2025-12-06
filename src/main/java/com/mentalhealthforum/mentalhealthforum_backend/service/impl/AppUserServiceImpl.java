package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InsufficientPermissionException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.KeycloakSyncException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserResponseMapper;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.mentalhealthforum.mentalhealthforum_backend.utils.ChangeUtils.*;


/**
 * Implementation of {@link AppUserService} handling user profile business logic.
 * Manages synchronization between Keycloak (identity provider) and local R2DBC database,
 * enforces privacy rules based on profile visibility and viewer privileges,
 * and handles context-aware user profile mapping.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Synchronize user data from Keycloak to local database</li>
 *   <li>Apply privacy rules for user profile visibility</li>
 *   <li>Enforce authorization for profile updates and deletions</li>
 *   <li>Provide paginated user listings with context-aware data</li>
 * </ul>
 *
 * @see AppUserService
 * @see UserResponseMapper
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
     * Creates or updates the R2DBC user profile by synchronizing data from Keycloak.
     * Fetches current roles and groups from Keycloak admin API and applies incremental updates.
     * Used for user registration and profile synchronization.
     *
     * @param keycloakUserDto Keycloak user data including identity fields
     * @param viewerContext Viewer context for privacy-aware response mapping, may be null for registration
     * @return Mono of user response with privacy rules applied
     */
    @Override
    public Mono<UserResponse> syncUserViaAdminClient(KeycloakUserDto keycloakUserDto, ViewerContext viewerContext){
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

                    // Save only if changes were detected to optimize database operations
                    return localNeedsUpdate ? appUserRepository.save(existingUser) : Mono.just(existingUser);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // User not found in local database - create new profile with default bio
                    // R2DBC performs an INSERT here.
                    userDetails.setBio(generateDefaultBio(keycloakUserDto.firstName(), keycloakUserDto.lastName()));
                    return appUserRepository.save(userDetails);
                }))
                .map(appUser ->  userResponseMapper.mapUserBasedOnContext(appUser, viewerContext));
    }
    /**
     * @deprecated replaced by {@link #syncUserViaAdminClient(KeycloakUserDto, ViewerContext)}
     * due to limitations in token-based synchronization. The userinfo endpoint lacks critical
     * metadata (createdTimestamp, enabled status) and is restricted to current user only.
     *
     * <p>For user synchronization, use:
     * <ul>
     *   <li>{@link #syncUserViaAdminClient(KeycloakUserDto, ViewerContext)} - Complete user data sync</li>
     *   <li>{@link #getAppUserWithContext(String, ViewerContext)} (String, String)} - User profile retrieval</li>
     *   <li>{@link #updateLocalProfile(String, ViewerContext, UpdateUserProfileRequest)} - Profile updates</li>
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
     * Fetches a single user profile with privacy rules applied.
     * Returns different data based on whether viewer is viewing their own profile,
     * the target user's profile visibility, and the viewer's privileges.
     *
     * @param userId Keycloak ID of the profile to fetch
     * @param viewerContext Authenticated viewer's context for privacy/self-determination
     * @return Mono of user response with appropriate privacy rules applied
     * @throws UserDoesNotExistException if no user found with given ID
     */
    @Override
    public Mono<UserResponse> getAppUserWithContext(String userId, ViewerContext viewerContext) {
        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new UserDoesNotExistException("User not found for ID: " + userId)))
                .map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, viewerContext));
    }
    /**
     * Fetches paginated list of all users with privacy rules applied for each.
     * Applies individual privacy rules per user based on their profile visibility
     * and the viewer's privileges.
     *
     * @param viewerContext Authenticated viewer's context for privacy rules
     * @param page Zero-indexed page number
     * @param size Number of items per page
     * @return Mono of paginated user responses with privacy rules applied
     */
        @Override
        public Mono<PaginatedResponse<UserResponse>> getAllAppUsersWithContext(ViewerContext viewerContext, int page, int size){

            String currentUserId = viewerContext != null? viewerContext.getUserId(): null;
            // Create a safe, deterministic comparator
            Comparator<AppUser> comparator = createUserComparator(currentUserId);

            Flux<AppUser> userResponseFlux =  appUserRepository.findAll()
                    .collectList()
                    .flatMapMany(appUsers -> {
                        // Sort with Java logic (current user first, then by display name)
                        List<AppUser> sortedUsers = appUsers.stream()
                                .sorted(comparator)
                                .collect(Collectors.toList());
                        return Flux.fromIterable(sortedUsers);
                    });

            // Apply the self flag before passing the Flux to the pagination utility
            Flux<UserResponse> userResFlux = userResponseFlux.map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, viewerContext));

            return PaginationUtils.paginate(page, size, userResFlux);
        }

        private Comparator<AppUser> createUserComparator(String currentUserId){
            return Comparator
                    // Current user first (false < true)
                    .comparing((AppUser appUser) -> !appUser.getKeycloakId().toString().equals(currentUserId))
                    // Then by displayName safely, nulls last, case-insensitive
                    .thenComparing(
                            Comparator.comparing(
                                    AppUser::getDisplayName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                            )
                    )
                    // Tiebreaker: To ensure deterministic order
                    .thenComparing(AppUser::getDateJoined);
        }
    /**
     * Updates locally managed profile fields for the authenticated user.
     * Only allows users to update their own profiles (authorization enforced).
     * Keycloak-authoritative fields (email, first/last name) are synchronized from Keycloak.
     *
     * @param userId Keycloak ID of the user to update
     * @param viewerContext Authenticated viewer's context for authorization
     * @param updateUserProfileRequest DTO containing fields to update
     * @return Mono of updated user response
     * @throws UserDoesNotExistException if no user found with given ID
     * @throws InsufficientPermissionException if viewer is not updating their own profile
     */
    @Override
    public Mono<UserResponse> updateLocalProfile(String userId, ViewerContext viewerContext, UpdateUserProfileRequest updateUserProfileRequest){
        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new UserDoesNotExistException("User not found for ID: " + userId)))
                .flatMap(appUser -> {
                    boolean localNeedsUpdate = false;

                    // Check if the user is updating their own profile
                    if (viewerContext == null || !viewerContext.getUserId().equals(userId)) {
                        return Mono.error(new InsufficientPermissionException("Forbidden: Cannot update another user's profile."));
                    }

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
                .map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, viewerContext));
    }
    /**
     * Deletes the local R2DBC profile for the authenticated user.
     * Only allows users to delete their own profiles (authorization enforced).
     * Does not affect Keycloak user account.
     *
     * @param userId Keycloak ID of the user whose profile is to be deleted
     * @param viewerContext Authenticated viewer's context for authorization
     * @return Mono signaling completion
     * @throws InsufficientPermissionException if viewer is not deleting their own profile
     */
    @Override
    public Mono<Void> deleteLocalProfile(String userId, ViewerContext viewerContext){
        // --- Authorization check ---
        if(viewerContext == null || !viewerContext.getUserId().equals(userId)){
            return Mono.error(new InsufficientPermissionException("Forbidden: Cannot delete another user's profile."));
        }
        return appUserRepository
                .findAppUserByKeycloakId(userId)
                .flatMap(appUserRepository::delete)
                .then();
    }
    /**
     * Generates a welcoming default bio for new users based on their name.
     * Used when creating initial user profiles during registration.
     *
     * @param firstName User's first name (maybe null)
     * @param lastName User's last name (maybe null)
     * @return Personalized welcome message or generic welcome if name not available
     */
    private String generateDefaultBio(String firstName, String lastName) {
        if (firstName != null && lastName != null) {
            return String.format("Hi, I'm %s %s. I'm here to connect, learn and grow. Let's support each other!", firstName, lastName);
        } else {
            return "Hi, I'm here to connect, learn and grow. Let's support each other!";
        }
    }
}
