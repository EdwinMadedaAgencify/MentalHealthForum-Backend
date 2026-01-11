package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingPolicy;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InsufficientPermissionException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.OnboardingPolicyViolationException;
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
import reactor.core.publisher.Mono;

import java.util.UUID;


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
            @Valid @RequestBody RegisterUserRequest registerUserRequest) {

        return userService.createUserInStaging(registerUserRequest)
                .map(email -> {
                    String message = "Registration request accepted. Please check your email to complete activation.";
                    return ResponseEntity.accepted().body(
                            new StandardSuccessResponse<>(message, email)
                    );
                });
    }

    @GetMapping("/{userId}")
    public Mono<ResponseEntity<StandardSuccessResponse<UserResponse>>> getUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        Mono<Void> syncMono = userService.getUser(String.valueOf(userId))
                .flatMap(keycloakUserDto -> appUserService.syncUserViaAdminClient(keycloakUserDto, viewerContext))
                .then();

        return syncMono
                .then(appUserService.getAppUserWithContext(String.valueOf(userId), viewerContext))
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
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserOnboardingProfileRequest updateUserProfileRequest) {

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        // --- Authorization check ---
        if(viewerContext == null || !viewerContext.getUserId().equals(String.valueOf(userId))){
            throw new InsufficientPermissionException("Forbidden: Cannot update another user's profile.");
        }

        // Try Keycloak update first
        return Mono.just(updateUserProfileRequest)
                .flatMap(updateUserOnboardingProfileRequest -> {
                    OnboardingPolicy.Result result = viewerContext.checkOnboardingPolicy(updateUserOnboardingProfileRequest);
                    if(!result.isSatisfied()){
                        return Mono.error(new OnboardingPolicyViolationException(result.violations()));
                    }
                   return userService.updateUserProfile(String.valueOf(userId), updateUserProfileRequest);
                })
                .flatMap(profileUpdateResult -> {
                    // Keycloak succeeded, now try local DB
                    return appUserService.updateLocalProfile(String.valueOf(userId), viewerContext, updateUserProfileRequest);
                })
                .map(updatedUser -> {

                    String message = "Profile updated successfully.";
                    if (updatedUser.getPendingEmail() != null) {
                        message = String.format(
                                "Profile updated. A verification link has been sent to %s. " +
                                        "Your email will update once verified.",
                                updateUserProfileRequest.email().toLowerCase()
                        );
                    }

                    return ResponseEntity.ok(new StandardSuccessResponse<>(message, updatedUser));
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
                    String message = "Password reset successfully.";
                    StandardSuccessResponse<Void> response = new StandardSuccessResponse<>(message);
                    return ResponseEntity.ok(response);
                }));
    }

    @DeleteMapping("/{userId}")
    public Mono<? extends ResponseEntity<?>> deleteUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId) {
        // userService.deleteUser returns Mono<Void>. We use thenReturn() to wait for completion
        // and then emit the 204 No Content response.

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        // --- Authorization check ---
        if(viewerContext == null || !viewerContext.getUserId().equals(String.valueOf(userId))){
            throw new InsufficientPermissionException("Forbidden: Cannot delete another user's profile.");
        }

        // Keycloak update Mono
        Mono<Void> keycloakDelete = userService.deleteUser(String.valueOf(userId))
                .doOnError(e -> log.error("Keycloak deletion failed: {}", e.getMessage()));

        // Local DB deletion
        Mono<Void> localDelete = appUserService.deleteLocalProfile(String.valueOf(userId), viewerContext)
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