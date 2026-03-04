package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingPolicy;
import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingStatusResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.user.UpdateUserProfileRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.user.UserResponse;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InsufficientPermissionException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.OnboardingPolicyViolationException;
import com.mentalhealthforum.mentalhealthforum_backend.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final UserService userService;
    private final AppUserService appUserService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public OnboardingController(
            PendingActionsService pendingActionsService,
            OnboardingService onboardingService,
            UserService userService,
            AppUserService appUserService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.onboardingService = onboardingService;
        this.userService = userService;
        this.appUserService = appUserService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }


    @GetMapping("/status")
    public Mono<ResponseEntity<StandardSuccessResponse<OnboardingStatusResponse>>> getOnboardingStatus(
            @AuthenticationPrincipal Jwt jwt
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        String userId = viewerContext.getUserId();

        return userService.getUser(userId)
                .flatMap(keycloakUserDto ->
                        appUserService.syncUserViaAdminClient(keycloakUserDto, viewerContext))
                .then(onboardingService.getOnboardingStatus(viewerContext)
                        .map(onboardingStatusResponse -> {
                            var success = new StandardSuccessResponse<>(
                                    "Onboarding status retrieved successfully.",
                                    onboardingStatusResponse
                            );
                            return ResponseEntity.ok(success);
                        }));
    }


    @PatchMapping("/{userId}/complete")
    public Mono<ResponseEntity<StandardSuccessResponse<UserResponse>>> completeOnboarding(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest) {

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

                    String message = "Onboarding completed successfully.";
                    if (updatedUser.getPendingEmail() != null) {
                        message = String.format(
                                "Onboarding complete. A verification link has been sent to %s. " +
                                        "Your email will update once verified.",
                                updateUserProfileRequest.email().toLowerCase()
                        );
                    }

                    return ResponseEntity.ok(new StandardSuccessResponse<>(message, updatedUser));
                });
    }

}
