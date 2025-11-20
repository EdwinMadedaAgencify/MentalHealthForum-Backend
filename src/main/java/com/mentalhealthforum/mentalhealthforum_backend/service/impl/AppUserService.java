package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.KeycloakSyncException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import com.mentalhealthforum.mentalhealthforum_backend.repository.AppUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.utils.PaginationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handles part of the user-related business logic, including profile synchronization
 * with Keycloak and data augmentation (like setting the 'isSelf' flag).
 */
@Service
public class AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);

    private final AppUserRepository appUserRepository;
    private final WebClient webClient;

    public AppUserService(
            AppUserRepository appUserRepository,
            WebClient.Builder webClientBuilder,
            KeycloakProperties keycloakProperties) {
        this.appUserRepository = appUserRepository;

        String authServerUrl = keycloakProperties.getAuthServerUrl();
        String realm = keycloakProperties.getRealm();

        String userInfoUri = String.format("%s/realms/%s/protocol/openid-connect/userinfo", authServerUrl, realm);

        this.webClient = webClientBuilder.baseUrl(userInfoUri).build();
    }

    /**
     * Creates the initial R2DBC profile immediately after Keycloak registration.
     * This is the backend's orchestration point for new users.
     */
    public Mono<AppUser> createInitialProfile(KeycloakUserDto keycloakUserDto){
        AppUser newAppUser = new AppUser(
                keycloakUserDto.id(),
                keycloakUserDto.email(),
                keycloakUserDto.username(),
                keycloakUserDto.firstName(),
                keycloakUserDto.lastName());

        newAppUser.setDateJoined(keycloakUserDto.getCreatedInstant());
        return appUserRepository.save(newAppUser);
    }
    /**
     * Orchestrates the user profile synchronization (The "Token Introspection" step).
     *
     * @param accessToken The JWT to be used for the authenticated call to Keycloak.
     * @return Mono<AppUser> The synchronized internal application User entity.
     */
    public Mono<AppUser> syncProfile(String accessToken){
        return getKeycloakUserInfo(accessToken)
                .flatMap(this::findOrCreateUser)
                .onErrorMap(e -> {
                    // Log the technical details of the error internally, but throw a generic exception for the user
                    log.error("Error syncing with Keycloak: {}", e.getMessage(), e);
                    return new KeycloakSyncException("An error occured while syncing user data.");
                });
    }
    /**
     * Step 1: Calls Keycloak's userinfo endpoint to get the authoritative profile.
     */
    private Mono<UserInfoDto> getKeycloakUserInfo(String accessToken) {
        return webClient.get()
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(UserInfoDto.class)
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
    public Mono<AppUser> getAppUserWithSelfFlag(String userId, String currentUserId) {
        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new UserDoesNotExistException("User not found for ID: " + userId)))
                .map(appUser -> setSelfFlagIfCurrentUser(appUser, currentUserId));
    }
    /**
     * Fetches all users, applies the 'isSelf' flag, and paginates the result.
     * @param currentUserId The Keycloak ID of the authenticated user.
     * @param page The requested page number (0-indexed).
     * @param size The number of items per page.
     * @return Mono<PaginatedResponse<AppUser>> A paginated structure containing the users.
     */
    public Mono<PaginatedResponse<AppUser>> getAllAppUsersWithSelfFlag(String currentUserId, int page, int size){
        // Fetch all users and set the 'self' flag on the current user
        Flux<AppUser> appUserFlux = appUserRepository.findAll();

        // Apply the self flag before passing the Flux to the pagination utility
        appUserFlux = appUserFlux.map(appUser -> setSelfFlagIfCurrentUser(appUser, currentUserId));

        return PaginationUtils.paginate(page, size, appUserFlux);
    }
    /**
     * Updates locally managed profile fields (e.g., bio).
     * @param userId The Keycloak ID of the user to update.
     * @param updateUserProfileRequest DTO containing the fields to update.
     */
    public Mono<AppUser> updateLocalProfile(String userId, UpdateUserProfileRequest updateUserProfileRequest){
        return appUserRepository.findAppUserByKeycloakId(userId)
                .switchIfEmpty(Mono.error(new UserDoesNotExistException("User not found for ID: " + userId)))
                .flatMap(appUser -> {
                    if(updateUserProfileRequest.bio() != null) appUser.setBio(updateUserProfileRequest.bio().trim());

                    return appUserRepository.save(appUser);
                });
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
     * Utility method to set the 'isSelf' flag on the user object.
     */
    private AppUser setSelfFlagIfCurrentUser(AppUser appUser, String currentUserId){
        if(appUser.getKeycloakId().equals(currentUserId)){
            appUser.setSelf(true);
        }
        return appUser;
    };
}
