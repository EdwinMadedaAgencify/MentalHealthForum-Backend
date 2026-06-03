package com.mentalhealthforum.mentalhealthforum_backend.controller.admin;

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

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserModerationController {

    private final UserModerationService userModerationService;
    private final JwtClaimsExtractor jwtClaimsExtractor;


    public AdminUserModerationController(
            UserModerationService userModerationService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.userModerationService = userModerationService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== SUSPENSIONS ====================

    @PostMapping("/{userId}/suspend")
    public Mono<ResponseEntity<StandardSuccessResponse<UserRestrictionResponse>>> suspendUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody SuspendUserRequest request
            ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.suspendUser(userId, request, viewerContext)
                .map(restriction -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "User suspended successfully",
                        restriction
                )));
    }

    @PatchMapping("/{userId}/unsuspend")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> unsuspendUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody UnSuspendRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.unsuspendUser(userId, request, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>(
                        "User suspended successfully"
                ))));
    }


    // ==================== BANS ====================

    @PostMapping("/{userId}/ban")
    public Mono<ResponseEntity<StandardSuccessResponse<UserRestrictionResponse>>> banUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody BanUserRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.banUser(userId, request, viewerContext)
                .map(restriction -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "User banned successfully",
                        restriction
                )));
    }

    @DeleteMapping("/{userId}/unban")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> unbanUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody UnbanRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userModerationService.unbanUser(userId, request, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>(
                        "User unbanned successfully"
                ))));
    }

}
