package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingStatusResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.PendingActionsResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/auth/status")
public class AuthStatusController {

    private final PendingActionsService pendingActionsService;
    private final OnboardingService onboardingService;
    private final UserService userService;
    private final AppUserService appUserService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public AuthStatusController(
            PendingActionsService pendingActionsService,
            OnboardingService onboardingService,
            UserService userService,
            AppUserService appUserService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.pendingActionsService = pendingActionsService;
        this.onboardingService = onboardingService;
        this.userService = userService;
        this.appUserService = appUserService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    @GetMapping("/pending-actions")
    public Mono<ResponseEntity<StandardSuccessResponse<PendingActionsResponse>>> getPendingActions(
            @RequestParam String identifier){

        return pendingActionsService.getPendingActions(identifier)
                .map(response ->{
                    var success = new StandardSuccessResponse<>(
                            "Pending actions retrieved successfully",
                            response
                    );
                    return ResponseEntity.ok(success);
                });
    }


    @GetMapping("/onboarding")
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

}
