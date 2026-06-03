package com.mentalhealthforum.mentalhealthforum_backend.controller.moderator;

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
@RequestMapping("/api/moderator/users")
public class ModeratorUserModerationController {

    private final UserModerationService userModerationService;
    private final JwtClaimsExtractor jwtClaimsExtractor;


    public ModeratorUserModerationController(
            UserModerationService userModerationService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.userModerationService = userModerationService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== WARNINGS ====================

    @PostMapping("/{userId}/warn")
    public Mono<ResponseEntity<StandardSuccessResponse<UserWarningResponse>>> warnUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody WarnUserRequest request
            ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.warnUser(userId, request, viewerContext)
                .map(warning -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "User warned successfully",
                        warning
                )));
    }


    @PatchMapping("/warnings/{warningId}/deactivate")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> deactivateWarning(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID warningId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.deactivateWarning(warningId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Warning deactivated successfully"
                ))));
    }


    // ==================== MUTES ====================

    @PostMapping("/{userId}/mute")
    public Mono<ResponseEntity<StandardSuccessResponse<UserRestrictionResponse>>> muteUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody MuteUserRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.muteUser(userId, request, viewerContext)
                .map(restriction -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "User muted successfully",
                        restriction
                )));
    }

    @PatchMapping("/{userId}/unmute")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> unmuteUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody UnMuteUserRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.unmuteUser(userId, request, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>(
                        "User unmuted successfully"
                ))));
    }

}
