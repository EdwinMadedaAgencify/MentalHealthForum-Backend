package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InsufficientPermissionException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import com.mentalhealthforum.mentalhealthforum_backend.service.AuthService;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.impl.AppUserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder; // Use standard UriComponentsBuilder
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final AppUserService appUserService;

    public UserController(UserService userService, AuthService authService, AppUserService appUserService) {
        this.userService = userService;
        this.appUserService = appUserService;
    }

    // -------------------------------------------------------------------------
    // USER MANAGEMENT (Refactored to Reactive)
    // -------------------------------------------------------------------------

    @PostMapping("/register")
    public Mono<ResponseEntity<StandardSuccessResponse<String>>> registerUser(
            @Valid @RequestBody RegisterUserRequest registerUserRequest,
            ServerWebExchange exchange) {

        final URI currentUri = exchange.getRequest().getURI();

        // Step 1: Register the user in Keycloak
        return userService.registerUser(registerUserRequest)
                .flatMap(userId -> {
                    // Step 2: Retrieve user data from Keycloak, Create the local profile and Return the userId
                    return userService.getUser(userId)
                            // 3. Create the initial internal application profile
                            .flatMap(appUserService::createInitialProfile)
                            // 4. Discard AppUser result, continue the stream with the original userId
                            .thenReturn(userId);
                })
                .map(userId -> {
                    // 5. Map the userId into a 201 Created Response
                    String message = "User registered successfully. Activation required.";
                    StandardSuccessResponse<String> response = new StandardSuccessResponse<>(message, userId);

                    // 6. Build the Location header URI for the newly created resource
                    URI location = UriComponentsBuilder.fromUri(currentUri)
                            .path("/{id}") // Appends "/{id}"
                            .buildAndExpand(userId) // Inserts the newly generated userId
                            .toUri(); // Finalizes the URI object

                    return ResponseEntity.created(location).body(response);
                });
    }

    @GetMapping("/{userId}")
    public Mono<ResponseEntity<StandardSuccessResponse<AppUser>>> getUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId
    ) {
        final String currentUserId = jwt.getSubject();

        Mono<Void> syncMono = currentUserId.equals(userId)
                ? appUserService.syncProfile(jwt.getTokenValue()).then()
                : Mono.empty();

        return syncMono
                .then(appUserService.getAppUserWithSelfFlag(userId, currentUserId))
                .map(user -> {
                    String message = "User details retrieved successfully";
                    StandardSuccessResponse<AppUser> response = new StandardSuccessResponse<>(message, user);
                    return ResponseEntity.ok(response);
                });
    }

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<AppUser>>>> getAllUsers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size){

        final String currentUserId = jwt.getSubject();

        // userService.getAllUsers returns Mono<PaginatedResponse<UserRepresentation>>
        return appUserService.getAllAppUsersWithSelfFlag(currentUserId, page, size)
                .map(paginatedUsers -> {
                    String message = "Paginated user records retrieved successfully.";
                    StandardSuccessResponse<PaginatedResponse<AppUser>> response = new StandardSuccessResponse<>(message, paginatedUsers);
                    return ResponseEntity.ok(response);
                });
    }

    @PatchMapping("/{userId}")
    public Mono<ResponseEntity<StandardSuccessResponse<AppUser>>> updateUserProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest) {

        final String currentUserId = jwt.getSubject();

        if(!currentUserId.equals(userId)){
            throw new InsufficientPermissionException("Forbidden: Cannot update another user's profile.");
        }

        // Keycloak update Mono
        Mono<KeycloakUserDto> keycloakUpdate = userService.updateUserProfile(userId, updateUserProfileRequest)
                .doOnError(e -> log.error("Keycloak update failed: {}", e.getMessage()));

        // Local DB update Mono
        Mono<AppUser>  localUpdate = appUserService.updateLocalProfile(userId, updateUserProfileRequest)
                .map(appUser -> {
                    appUser.setSelf(true);
                    return appUser;
                })
                .doOnError(e -> log.error("Local DB update failed: {}", e.getMessage()));

        return Mono.zipDelayError(keycloakUpdate, localUpdate)
                .map(tuple ->{
                    String message = "User updated successfully.";
                    StandardSuccessResponse<AppUser> response = new StandardSuccessResponse<>(message, tuple.getT2());
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    // Handle partial or total failure gracefully
                    log.error("Profile update encountered an error: {}", e.getMessage(), e);
                    return appUserService.getAppUserWithSelfFlag(currentUserId, currentUserId)
                            .map(userWithSelf ->{
                                String message = "Profile update partially failed: " + e.getMessage();
                                StandardSuccessResponse<AppUser> response = new StandardSuccessResponse<>(message, userWithSelf);
                                return ResponseEntity.status(500).body(response);
                            });
                });
    }

    @PostMapping("/reset-password")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> resetPassword(
            @RequestParam String userId,
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {

        // userService.resetPassword returns Mono<Void>. We use then() to wait for completion.
        return userService.resetPassword(userId, resetPasswordRequest)
                .then(Mono.fromCallable(() -> {
                    String message = "AppUser reset password successfully.";
                    StandardSuccessResponse<Void> response = new StandardSuccessResponse<>(message);
                    return ResponseEntity.ok(response);
                }));
    }

    @DeleteMapping("/{userId}")
    public Mono<? extends ResponseEntity<?>> deleteUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {
        // userService.deleteUser returns Mono<Void>. We use thenReturn() to wait for completion
        // and then emit the 204 No Content response.

        final String currentUserId = jwt.getSubject();

        // --- Authorization check ---
        if(!currentUserId.equals(userId)){
            throw new InsufficientPermissionException("Forbidden: Cannot delete another user's profile.");
        }

        // Keycloak update Mono
        Mono<Void> keycloakDelete = userService.deleteUser(userId)
                .doOnError(e -> log.error("Keycloak deletion failed: {}", e.getMessage()));

        // Local DB deletion
        Mono<Void> localDelete = appUserService.deleteLocalProfile(userId)
                .doOnError(e -> log.error("Local DB deletion failed: {}", e.getMessage()));

        return Mono.zipDelayError(keycloakDelete, localDelete)
                .thenReturn(ResponseEntity.noContent().build())
                .onErrorResume(e -> {
                    log.error("User deletion partially failed: {}", e.getMessage(), e);
                    // Return 500 with a message; 204 is only for full success
                    StandardSuccessResponse<Void> response = new StandardSuccessResponse<>("User deletion partially failed: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }
}