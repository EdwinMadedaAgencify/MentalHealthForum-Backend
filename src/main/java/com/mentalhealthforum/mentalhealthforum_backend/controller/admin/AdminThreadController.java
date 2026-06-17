package com.mentalhealthforum.mentalhealthforum_backend.controller.admin;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.service.ThreadService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/admin/forum/threads")
public class AdminThreadController {

    private final ThreadService threadService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public AdminThreadController(
            ThreadService threadService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.threadService = threadService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    @DeleteMapping("/{threadId}/permanent")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> permanentlyDeleteThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.permanentlyDeleteThread(threadId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Thread permanently deleted Successfully"))));
    }
}
