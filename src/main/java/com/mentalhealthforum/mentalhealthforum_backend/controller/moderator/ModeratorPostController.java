package com.mentalhealthforum.mentalhealthforum_backend.controller.moderator;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.PostResponse;
import com.mentalhealthforum.mentalhealthforum_backend.enums.PostType;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import com.mentalhealthforum.mentalhealthforum_backend.service.PostService;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/moderator/forum/posts")
public class ModeratorPostController {

    private final PostService postService;
    private final JwtClaimsExtractor jwtClaimsExtractor;


    public ModeratorPostController(PostService postService, JwtClaimsExtractor jwtClaimsExtractor) {
        this.postService = postService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== MODERATION ACTIONS ====================

    @DeleteMapping("/{postId}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> softDeleteAnyPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.softDeleteAnyPost(postId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Post soft deleted successfully"))));
    }

    @PostMapping("/{postId}/restore")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> restorePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
            ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.restorePost(postId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Post restored successfully"))));
    }

    @PostMapping("/{postId}/flag")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> flagPostAsModerator(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.flagPostAsModerator(postId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Post flagged for review"))));

    }

    @DeleteMapping("/{postId}/flag")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> clearFlag(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.clearFlag(postId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Flag cleared successfully"))));

    }

    @GetMapping("/flagged")
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<PostResponse>>>> getFlaggedPosts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "author_id") @Parameter(name = "author_id", description = "Filter by author user ID") UUID authorId,
            @RequestParam(required = false, name = "post_type") @Parameter(name = "post_type", description = "Filter by post type: REPLY, ANSWER, SYSTEM_MESSAGE, MODERATOR_NOTE") PostType postType,
            @RequestParam(required = false, name = "has_content_warning") @Parameter(name = "has_content_warning", description = "Filter posts with content warnings") Boolean hasContentWarning,
            @RequestParam(defaultValue = "", name = "search") @Parameter(name = "search", description = "Search by content (case-insensitive contains)") String search,
            @RequestParam(defaultValue = "created_at", name = "sort_by") @Parameter(name = "sort_by", description = "Field to sort by: created_at, updated_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction") @Parameter(name = "sort_direction", description = "Sort direction: asc or desc") String sortDirection
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.getFlaggedPosts(page, size,authorId, postType, hasContentWarning, search, sortBy, sortDirection, viewerContext)
                .map(paginatedPosts ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Flagged posts retrieved successfully", paginatedPosts)));
    }
}
