package com.mentalhealthforum.mentalhealthforum_backend.controller.admin;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import com.mentalhealthforum.mentalhealthforum_backend.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/admin/forum/posts")
public class AdminPostController {

    private final PostService postService;
    private final JwtClaimsExtractor jwtClaimsExtractor;


    public AdminPostController(PostService postService, JwtClaimsExtractor jwtClaimsExtractor) {
        this.postService = postService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== MODERATION ACTIONS ====================

    @DeleteMapping("/{postId}/permanent")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> permanentlyDeletePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.permanentlyDeletePost(postId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Post permanently deleted successfully"))));
    }

}
