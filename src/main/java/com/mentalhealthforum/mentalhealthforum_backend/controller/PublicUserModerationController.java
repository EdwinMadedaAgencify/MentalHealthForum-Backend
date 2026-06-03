package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserModerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class PublicUserModerationController {

    private final UserModerationService userModerationService;
    private final JwtClaimsExtractor jwtClaimsExtractor;


    public PublicUserModerationController(
            UserModerationService userModerationService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.userModerationService = userModerationService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== SUSPENSIONS ====================

    @GetMapping("/{userId}/suspend/status")
    public Mono<ResponseEntity<StandardSuccessResponse<UserRestrictionResponse>>> getUserSuspendStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
            ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.getActiveSuspendForUser(userId, viewerContext)
                .map(restriction -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Suspension status retrieved successfully",
                        restriction
                )));
    }

    // ==================== BANS ====================

    @GetMapping("/{userId}/ban/status")
    public Mono<ResponseEntity<StandardSuccessResponse<UserRestrictionResponse>>> getUserBanStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.getActiveBanForUser(userId, viewerContext)
                .map(restriction -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Ban status retrieved successfully",
                        restriction
                )));
    }

    // ==================== MUTES ====================

    @GetMapping("/{userId}/mute/status")
    public Mono<ResponseEntity<StandardSuccessResponse<UserRestrictionResponse>>> getUserMuteStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.getActiveMuteForUser(userId, viewerContext)
                .map(restriction -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Mute status retrieved successfully",
                        restriction
                )));
    }

    // ==================== WARNINGS ====================

    @GetMapping("/{userId}/warnings")
    public Mono<ResponseEntity<StandardSuccessResponse<List<UserWarningResponse>>>> getUserWarnings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.getUserWarnings(userId, viewerContext)
                .collectList()
                .map(warnings -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "User warnings retrieved successfully", warnings
                )));
    }

    @GetMapping("/{userId}/warning-count")
    public Mono<ResponseEntity<StandardSuccessResponse<Integer>>> getUserActiveWarningCount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.getUserActiveWarningCount(userId, viewerContext)
                .map(count -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Active warning count retrieved", count
                )));
    }

    @PatchMapping("/warnings/{warningId}/acknowledge")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> acknowledgeWarning(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID warningId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.acknowledgeWarning(warningId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Warning acknowledged successfully"
                ))));
    }

}
