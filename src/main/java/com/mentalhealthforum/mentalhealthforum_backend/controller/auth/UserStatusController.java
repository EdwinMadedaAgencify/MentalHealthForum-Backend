package com.mentalhealthforum.mentalhealthforum_backend.controller.auth;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.userStatus.UserStatusResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth/status/user")
public class UserStatusController {

    private final UserStatusService userStatusService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public UserStatusController(
            UserStatusService userStatusService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.userStatusService = userStatusService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    /**
     * @deprecated Use SecurityExceptionHandler for user-friendly 403 messages instead.
     * Kept for backward compatibility. Will be removed in a future version.
     */
    @GetMapping
    @Deprecated
    public Mono<ResponseEntity<StandardSuccessResponse<UserStatusResponse>>> getUserStatus(
            @AuthenticationPrincipal Jwt jwt
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return userStatusService.getUserStatus(userId, viewerContext)
                .map(response -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("User status retrieved successfully", response)
                ));
    }

}
