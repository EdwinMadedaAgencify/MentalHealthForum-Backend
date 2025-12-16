package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InsufficientPermissionException;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.impl.AppUserServiceImpl;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public UserController(UserService userService, AppUserServiceImpl appUserService, JwtClaimsExtractor jwtClaimsExtractor) {
        this.userService = userService;
        this.appUserService = appUserService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
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
                            .flatMap(keycloakUserDto -> appUserService.syncUserViaAdminClient(keycloakUserDto, null))
                            // 4. Discard AppUser result, continue the stream with the original userId
                            .thenReturn(userId);
                })
                .map(userId -> {
                    // 5. Map the userId into a 201 Created Response
                    String message = "User registered successfully. Activation required.";
                    StandardSuccessResponse<String> response = new StandardSuccessResponse<>(message, userId);

                    // 6. Build the Location header URI for the newly created resource
                    URI location = UriComponentsBuilder.fromUri(currentUri)
                            .path("/{userId}") // Appends "/{userId}"
                            .buildAndExpand(userId) // Inserts the newly generated userId
                            .toUri(); // Finalizes the URI object

                    return ResponseEntity.created(location).body(response);
                });
    }

    @GetMapping("/{userId}")
    public Mono<ResponseEntity<StandardSuccessResponse<UserResponse>>> getUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        Mono<Void> syncMono = userService.getUser(userId)
                .flatMap(keycloakUserDto -> appUserService.syncUserViaAdminClient(keycloakUserDto, viewerContext))
                .then();

        return syncMono
                .then(appUserService.getAppUserWithContext(userId, viewerContext))
                .map(user -> {
                    String message = "User details retrieved successfully";
                    StandardSuccessResponse<UserResponse> response = new StandardSuccessResponse<>(message, user);
                    return ResponseEntity.ok(response);
                });
    }

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<UserResponse>>>> getAllUsers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "true", name = "current_user_first") @Parameter(name = "current_user_first") boolean currentUserFirst,
            @RequestParam(required = false, name = "is_active") @Parameter(name = "is_active") Boolean isActive,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String[] groups,
            @RequestParam(defaultValue = "display_name", name = "sort_by")
            @Parameter(name = "sort_by", description = "Field to sort by: display_name, date_joined, posts_count, reputation_score, last_posted_at, last_active_at")
            String sortBy,
            @RequestParam(required = false, name = "sort_direction")
            @Parameter(name = "sort_direction", description = "Sort direction: asc or desc")
            String sortDirection,
            @RequestParam(required = false, name = "search")
            @Parameter(name = "search", description = "Search display_name (case-insensitive contains)")
            String search
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        // userService.getAllUsers returns Mono<PaginatedResponse<UserRepresentation>>
        return appUserService.getAllAppUsersWithContext(viewerContext, page, size, currentUserFirst, isActive, role, groups, sortBy, sortDirection, search)
                .map(paginatedUsers -> {
                    String message = "Paginated user records retrieved successfully.";
                    StandardSuccessResponse<PaginatedResponse<UserResponse>> response = new StandardSuccessResponse<>(message, paginatedUsers);
                    return ResponseEntity.ok(response);
                });
    }

    @PatchMapping("/{userId}")
    public Mono<ResponseEntity<StandardSuccessResponse<UserResponse>>> updateUserProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest) {

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        // --- Authorization check ---
        if(viewerContext == null || !viewerContext.getUserId().equals(userId)){
            throw new InsufficientPermissionException("Forbidden: Cannot update another user's profile.");
        }

        // Try Keycloak update first
        return userService.updateUserProfile(userId, updateUserProfileRequest)
                .flatMap(keycloakUserDto -> {
                    // Keycloak succeeded, now try local DB
                    return appUserService.updateLocalProfile(userId, viewerContext, updateUserProfileRequest)
                            .onErrorResume(localError -> {
                                // Local DB failed, but Keycloak succeeded
                                log.error("Local DB update failed after Keycloak success: {}", localError.getMessage());
                                // Return current profile as fallback
                                return appUserService.getAppUserWithContext(userId, viewerContext);
                            });
                })
                .map(updatedUser -> {
                    String message = "User updated successfully.";
                    return ResponseEntity.ok(new StandardSuccessResponse<>(message, updatedUser));
                })
                .onErrorResume(keycloakError -> {
                    // Keycloak update failed
                    log.error("Keycloak update failed: {}", keycloakError.getMessage());
                    return appUserService.getAppUserWithContext(userId, viewerContext)
                            .map(currentProfile -> {
                                String message = "Profile update failed. Please try again later.";
                                StandardSuccessResponse<UserResponse> response = new StandardSuccessResponse<>(message, currentProfile);
                                return ResponseEntity.status(500).body(response);
                            });
                });
    }


    @PostMapping("/reset-password")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String userId,
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        // --- Authorization check ---
        if(viewerContext == null || !viewerContext.getUserId().equals(userId)){
            throw new InsufficientPermissionException("Forbidden: Cannot reset another user's password.");
        }

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

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        // --- Authorization check ---
        if(viewerContext == null || !viewerContext.getUserId().equals(userId)){
            throw new InsufficientPermissionException("Forbidden: Cannot delete another user's profile.");
        }

        // Keycloak update Mono
        Mono<Void> keycloakDelete = userService.deleteUser(userId)
                .doOnError(e -> log.error("Keycloak deletion failed: {}", e.getMessage()));

        // Local DB deletion
        Mono<Void> localDelete = appUserService.deleteLocalProfile(userId, viewerContext)
                .doOnError(e -> log.error("Local DB deletion failed: {}", e.getMessage()));

        return Mono.zip(keycloakDelete, localDelete)
                .thenReturn(ResponseEntity.noContent().build())
                .onErrorResume(e -> {
                    log.error("User deletion partially failed: {}", e.getMessage(), e);
                    // Return 500 with a message; 204 is only for full success
                    StandardSuccessResponse<Void> response = new StandardSuccessResponse<>("User deletion partially failed");
                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }
}