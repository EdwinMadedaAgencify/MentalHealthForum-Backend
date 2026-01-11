package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingPolicy;
import com.mentalhealthforum.mentalhealthforum_backend.enums.InternalRole;
import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.*;
import com.mentalhealthforum.mentalhealthforum_backend.model.AdminInvitationRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.VerificationTokenRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AdminInvitationService;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserResponseMapper;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import com.mentalhealthforum.mentalhealthforum_backend.repository.AppUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.KeycloakAdminManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
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
    private final NovuServiceImpl novuServiceImpl;
    private final UserResponseMapper userResponseMapper;
    private final AdminInvitationService adminInvitationService;
    private final AdminInvitationRepository adminInvitationRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final WebClient webClient;
    private final  String userInfoUri;

    private record KeycloakExtraDetails(Set<String> roles, Set<String> groups){}

    public AppUserServiceImpl(
            AppUserRepository appUserRepository,
            WebClient.Builder webClientBuilder,
            KeycloakProperties keycloakProperties,
            KeycloakAdminManager adminManager,
            NovuServiceImpl novuServiceImpl,
            UserResponseMapper userResponseMapper,
            AdminInvitationService adminInvitationService,
            AdminInvitationRepository adminInvitationRepository,
            VerificationTokenRepository verificationTokenRepository) {
        this.appUserRepository = appUserRepository;
        this.adminManager = adminManager;
        this.novuServiceImpl = novuServiceImpl;
        this.userResponseMapper = userResponseMapper;
        this.adminInvitationService = adminInvitationService;
        this.adminInvitationRepository = adminInvitationRepository;
        this.verificationTokenRepository = verificationTokenRepository;

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
        //  Create the base object (No I/O here, just memory)
        AppUser userDetails = new AppUser(
                keycloakUserDto.userId(),
                keycloakUserDto.email(),
                keycloakUserDto.username(),
                keycloakUserDto.firstName(),
                keycloakUserDto.lastName());
        return fetchKeycloakExtraDetails(keycloakUserDto.userId())
                .flatMap(details -> {
                    // Apply the real-time truth from Keycloak to our template

                    // Set display name
                    //userDetails.setDisplayName(userDetails.getDisplayName());

                    // Set fields that are only relevant on INSERT
                    userDetails.setIsEnabled(keycloakUserDto.enabled());
                    userDetails.setDateJoined(keycloakUserDto.getCreatedInstant());

                    // Set fields that are relevant on EVERY sync
                    userDetails.setLastSyncedAt(Instant.now());
                    userDetails.setRoles(details.roles());
                    userDetails.setGroups(details.groups());

                    // 3. Search for existing local user
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
                                // 1. Check if the user is ready to be synced in our local DB
                                return isReadyForSyncing(keycloakUserDto)
                                        .flatMap(ready -> {

                                            // GATEKEEPER: NOT READY
                                            if(!ready){
                                                log.debug("User {} not ready for sync. Checking partial access.", keycloakUserDto.userId());

//                                                // Allow Admin or Self to see the "Ghost Profile"
//                                                if(viewerContext.isAdmin() || userDetails.getKeycloakId().toString().equals(viewerContext.getUserId())){
//                                                    return Mono.just(userDetails);
//                                                }
                                                return Mono.empty();
                                            }
                                            // GATEKEEPER: READY (Atomic Transition)
                                            log.info("User {} cleared all hurdles. Transitioning to AppUser.", keycloakUserDto.userId());
//                                            userDetails.setBio(generateDefaultBio(keycloakUserDto.firstName(), keycloakUserDto.lastName()));

                                            return appUserRepository.save(userDetails)
                                                    .flatMap(savedUser -> {
                                                        // 4. Trigger simultaneous side effects
                                                        return adminInvitationService.completeInvitation(savedUser.getKeycloakId())
                                                                .then(Mono.fromRunnable(() ->
                                                                        adminManager.markAsSyncedLocally(savedUser.getKeycloakId().toString(), true))
                                                                        .subscribeOn(Schedulers.boundedElastic())
                                                                )
                                                                .thenReturn(savedUser);
                                                    });
                                        });
                            }));
                })
                .flatMap(appUser -> {
                    if(appUser.getId() != null){
                        return novuServiceImpl.upsertSubscriber(appUser).thenReturn(appUser);
                    }
                    return Mono.just(appUser);
                })
                .flatMap(appUser -> evaluateOnboardingPolicyCompliance(appUser, viewerContext).thenReturn(appUser))
                .flatMap(this::enrichWithPendingEmail)
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
     *   <li>{@link #updateLocalProfile(String, ViewerContext, UpdateUserOnboardingProfileRequest)} - Profile updates</li>
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
                .switchIfEmpty(Mono.error(new UserDoesNotExistException()))
                .flatMap(appUser -> {
                    // Fetch Keycloak roles for Admins
                    if( viewerContext.isAdmin()){
                        return Mono.fromCallable(()-> adminManager.getUserRealmRoles(appUser.getKeycloakId().toString()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .map(HashSet::new)
                                .doOnNext(appUser::setRoles)
                                .thenReturn(appUser);
                    }
                    return Mono.just(appUser);
                })
                .flatMap(this::enrichWithPendingEmail)
                // Mapper now receives appUser with pendingEmail already attached
                .map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, viewerContext));
    }
    /**
     * Fetches paginated list of all users with privacy rules applied for each.
     * Applies individual privacy rules per user based on their profile visibility
     * and the viewer's privileges.
     *
     * @param viewerContext    Authenticated viewer context used to determine field visibility
     * @param page             Zero-indexed page number to retrieve
     * @param size             Number of users to return per page
     * @param currentUserFirst Whether to place the current user first on page 0
     * @param isActive         Optional filter restricting results by active/inactive status
     * @param role             Optional role filter; null means no role restriction
     * @param groups           Optional group filter; empty array is treated as no filter
     * @param sortBy           Field to sort by; falls back to a safe default when invalid
     * @param sortDirection    Sort direction ("asc" or "desc"); defaults by field when null
     * @param search           Optional search query; blank values ignored
     * @return Mono of paginated user responses with privacy rules applied
     */
    @Override
    public Mono<PaginatedResponse<UserResponse>> getAllAppUsersWithContext(
            ViewerContext viewerContext,
            int page,
            int size,
            boolean currentUserFirst,
            Boolean isActive,
            String role,
            String[] groups,
            String sortBy,
            String sortDirection,
            String search){


        if (page < 0 || size <= 0) {
            log.error("Invalid pagination parameters: page={}, size={}", page, size);
            throw new InvalidPaginationException();
        }

        String currentUserId = viewerContext != null? viewerContext.getUserId() : null;

        int offset = page * size;

        // Convert empty array to nullAdminInvitation
        String[] effectiveGroups = (groups == null || groups.length == 0) ? null : groups;

        // Convert empty search to null
        String effectiveSearch = (search == null || search.trim().isEmpty()) ? null: search.trim();

        String normalizedSortBy = validateAndNormalizeSortBy(sortBy);
        String normalizedDirection = determineSortDirection(sortDirection, normalizedSortBy);

        // Only apply current user first on page 0
        boolean applyCurrentUserFirst = currentUserFirst && page == 0;

        Flux<AppUser> appUsersFlux = appUserRepository.findAllPaginated(
                isActive, role, effectiveGroups,currentUserId, applyCurrentUserFirst, normalizedSortBy, normalizedDirection, effectiveSearch, size, offset
        );

        Mono<Long> totalCount = appUserRepository.countAll(isActive, role, effectiveGroups);

        return Mono.zip(appUsersFlux.collectList(), totalCount)
                .map(tuple -> {
                    List<AppUser> appUsers = tuple.getT1();
                    long total = tuple.getT2();

                    List<UserResponse> content = appUsers.stream()
                            .map(appUser -> userResponseMapper.mapUserBasedOnContext(appUser, viewerContext))
                            .collect(Collectors.toList());

                    return new PaginatedResponse<>(content, page, size, total);
                });
    }

    private String validateAndNormalizeSortBy(String sortBy){
        // Allowed sort fields
        Set<String> allowedFields = Set.of("display_name","date_joined", "posts_count", "reputation_score", "last_posted_at", "last_active_at");
        if(!allowedFields.contains(sortBy)){
            return "display_name"; // Default to alphabetical
        }
        return sortBy;
    }

    private String determineSortDirection(String sortDirection, String sortBy){
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection)? "DESC": "ASC";
        }
        // Natural defaults based on field
        return switch (sortBy) {
            case "date_joined", "posts_count", "reputation_score", "last_posted_at", "last_active_at" -> "DESC";
            default -> "ASC"; // display_name
        };
    }

    @Override
    public Mono<Void> updateLocalEmail(String userId, String newEmail){
        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new UserDoesNotExistException()))
                .flatMap(appUser -> {
                    boolean localEmailNeedsUpdate = setIfChangedStrict(newEmail, appUser.getEmail(), appUser::setEmail);
                    return localEmailNeedsUpdate? appUserRepository.save(appUser): Mono.just(appUser);
                })
                .doOnSuccess(user -> log.info("Successfully synced local email for user: {}", userId))
                .then();
    }

    /**
     * Updates locally managed profile fields for the authenticated user.
     * Only allows users to update their own profiles (authorization enforced).
     * Keycloak-authoritative fields (email, first/last name) are synchronized from Keycloak.
     *
     * @param userId Keycloak ID of the user to update
     * @param viewerContext Authenticated viewer context used to determine field visibility
     * @param updateUserProfileRequest DTO containing fields to update
     * @return Mono of updated user response
     * @throws UserDoesNotExistException if no user found with given ID
     * @throws InsufficientPermissionException if viewer is not updating their own profile
     */
    @Override
    public Mono<UserResponse> updateLocalProfile(String userId, ViewerContext viewerContext, UpdateUserOnboardingProfileRequest updateUserProfileRequest){

        return validateOnboardingPolicy(updateUserProfileRequest, viewerContext)
                .then(appUserRepository.findAppUserByKeycloakId(userId))
                .switchIfEmpty(Mono.error(new UserDoesNotExistException()))
                .flatMap(appUser -> {
                    boolean localNeedsUpdate = false;

                    // Check if the user is updating their own profile
                    if (!viewerContext.getUserId().equals(userId)) {
                        return Mono.error(new InsufficientPermissionException("Forbidden: Cannot update another user's profile."));
                    }

                        // --- Keycloak-authoritative fields ---
                        // Email not updated until verified in VerificationService.processVerification
                        localNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.firstName(), appUser.getFirstName(), appUser::setFirstName);
                        localNeedsUpdate |= setIfChangedStrict(updateUserProfileRequest.lastName(), appUser.getLastName(), appUser::setLastName);

                        // --- User-controlled fields (allow null/blank to clear) --
                        localNeedsUpdate |= setIfChangedAllowNull(updateUserProfileRequest.displayName(), appUser.displayName(), appUser::setDisplayName);
                        localNeedsUpdate |= setIfChangedAllowNull(updateUserProfileRequest.bio(), appUser.bio(), appUser::setBio);
                        localNeedsUpdate |= setIfChangedAllowNull(updateUserProfileRequest.avatarUrl(), appUser.getAvatarUrl(), appUser::setAvatarUrl);
                        localNeedsUpdate |= setIfChangedAllowNull(updateUserProfileRequest.timezone(), appUser.timezone(), appUser::setTimezone);

                        // Enum fields
                        localNeedsUpdate |= setIfChangedAllowNull(
                                updateUserProfileRequest.profileVisibility(),
                                appUser.getProfileVisibility(),
                                appUser::setProfileVisibility);

                        localNeedsUpdate |= setIfChangedAllowNull(
                                updateUserProfileRequest.supportRole(),
                                appUser.supportRole(),
                                appUser::setSupportRole);

                        // Notification preferences (JSON-backed object)
                        localNeedsUpdate |= setIfChangedAllowNull(
                                updateUserProfileRequest.notificationPreferences(),
                                appUser.getNotificationPreferences(),
                                appUser::setNotificationPreferences);

                    // --- Persist only if any changes ---
                    return localNeedsUpdate ? appUserRepository.save(appUser) : Mono.just(appUser);
                })
                .flatMap(savedUser ->
                        novuServiceImpl.upsertSubscriber(savedUser)
                                .then(evaluateOnboardingPolicyCompliance(savedUser, viewerContext))
                                .thenReturn(savedUser))
                .flatMap(this::enrichWithPendingEmail)
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
    @Deprecated
    private String generateDefaultBio(String firstName, String lastName) {
        if (firstName != null && lastName != null) {
            return String.format("Hi, I'm %s %s. I'm here to connect, learn and grow. Let's support each other!", firstName, lastName);
        } else {
            return "Hi, I'm here to connect, learn and grow. Let's support each other!";
        }
    }

    private Mono<Boolean> isReadyForSyncing(KeycloakUserDto keycloakUserDto){
        return adminInvitationRepository.findByKeycloakId(UUID.fromString(keycloakUserDto.userId()))
                .map(adminInvitation -> {
                    // The user is only ready to move to app_users
                    // once they've cleared the final staging hurdle.
                    return keycloakUserDto.emailVerified()
                            && adminInvitation.getCurrentStage() == OnboardingStage.AWAITING_PROFILE_COMPLETION;
                })
                .defaultIfEmpty(keycloakUserDto.emailVerified()); // Fallback for self-registered who bypass the lobby
    }

    private Mono<UpdateUserOnboardingProfileRequest> validateOnboardingPolicy(
            UpdateUserOnboardingProfileRequest updateUserOnboardingProfileRequest,
            ViewerContext viewerContext){

        OnboardingPolicy.Result result = viewerContext.checkOnboardingPolicy(updateUserOnboardingProfileRequest);

        if(!result.isSatisfied()){
            return Mono.error(new OnboardingPolicyViolationException(result.violations()));
        }
        return Mono.just(updateUserOnboardingProfileRequest);
    }

    private Mono<Void> evaluateOnboardingPolicyCompliance(AppUser appUser, ViewerContext viewerContext){
       return Mono.fromRunnable(()-> {
                   String userId = String.valueOf(appUser.getKeycloakId());

                   if(viewerContext.checkOnboardingPolicy(appUser).isSatisfied()){
                       adminManager.removeInternalRole(userId, InternalRole.ONBOARDING);
                   }
                   else {
                       adminManager.assignInternalRole(userId, InternalRole.ONBOARDING);
                   }
               }).subscribeOn(Schedulers.boundedElastic())
               .then();
    }

    private Mono<KeycloakExtraDetails> fetchKeycloakExtraDetails(String userId){
        return Mono.fromCallable(()-> {
            List<String> roles = adminManager.getUserRealmRolesFromGroups(userId);
            List<String> groups = adminManager.getUserGroups(userId);
            return new KeycloakExtraDetails(new HashSet<>(roles), new HashSet<>(groups));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Enriches the AppUser model with transient metadata from active verification tokens.
     * This ensures the 'pendingEmail' field is populated across all retrieval and sync flows.
     */
    private Mono<AppUser> enrichWithPendingEmail(AppUser appUser){
        //If the user hasn't been saved yet (no email), just return
        if(appUser.getEmail() == null){
            return Mono.just(appUser);
        }

        return verificationTokenRepository.findByEmailAndType(appUser.getEmail(), VerificationType.APP_USER)
                .map(verificationToken -> {
                    appUser.setPendingEmail(verificationToken.getNewValue());
                    return appUser;
                })
                .defaultIfEmpty(appUser);
    }
}
