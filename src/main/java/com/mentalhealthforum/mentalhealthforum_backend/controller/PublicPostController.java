package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.CreatePostRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.FlagPostRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.PostResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.UpdatePostRequest;
import com.mentalhealthforum.mentalhealthforum_backend.enums.PostType;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import com.mentalhealthforum.mentalhealthforum_backend.service.PostService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/forum/posts")
public class PublicPostController {

    private final PostService postService;
    private final JwtClaimsExtractor jwtClaimsExtractor;


    public PublicPostController(PostService postService, JwtClaimsExtractor jwtClaimsExtractor) {
        this.postService = postService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== CREATE / READ ====================

    @PostMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PostResponse>>> createPost(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePostRequest request
            ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.createPost(request, viewerContext)
                .map(post -> ResponseEntity.status(HttpStatus.CREATED)
                .body(new StandardSuccessResponse<>("Post created successfully", post)));
    }

    @GetMapping("/{postId}")
    public Mono<ResponseEntity<StandardSuccessResponse<PostResponse>>> getPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.getPost(postId, viewerContext)
                .map(post ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Post retrieved successfully", post)));

    }

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<PostResponse>>>> getAllPosts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "thread_id") @Parameter(name = "thread_id", description = "Filter by thread ID") UUID threadId,
            @RequestParam(required = false, name = "author_id") @Parameter(name = "author_id", description = "Filter by author user ID") UUID authorId,
            @RequestParam(required = false, name = "parent_post_id") @Parameter(name = "parent_post_id", description = "Filter by parent post ID (for threaded replies)") UUID parentPostId,
            @RequestParam(required = false, name = "post_type") @Parameter(name = "post_type", description = "Filter by post type: REPLY, ANSWER, SYSTEM_MESSAGE, MODERATOR_NOTE") PostType postType,
            @RequestParam(required = false, name = "has_content_warning") @Parameter(name = "has_content_warning", description = "Filter posts with content warnings") Boolean hasContentWarning,
            @RequestParam(defaultValue = "", name = "search") @Parameter(name = "search", description = "Search by content (case-insensitive contains)") String search,
            @RequestParam(defaultValue = "created_at", name = "sort_by") @Parameter(name = "sort_by", description = "Field to sort by: created_at, updated_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction") @Parameter(name = "sort_direction", description = "Sort direction: asc or desc") String sortDirection
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService. getAllPosts( page, size, threadId, authorId, parentPostId, postType, hasContentWarning, search,sortBy, sortDirection, viewerContext)
                .map(paginatedPosts ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Posts retrieved successfully", paginatedPosts)));
    }

    @PutMapping("/{postId}")
    public Mono<ResponseEntity<StandardSuccessResponse<PostResponse>>> updateOwnPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid@RequestBody UpdatePostRequest request
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.updateOwnPost(postId,request, viewerContext)
                .map(post ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Post updated successfully", post)));
    }

    @DeleteMapping("/{postId}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> updateOwnPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.softDeleteOwnPost(postId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Post soft deleted successfully"))));
    }

    // ==================== USER FLAG ACTIONS ====================
    @PostMapping("/{postId}/flag")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> flagPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid @RequestBody FlagPostRequest request
            ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return postService.flagPostAsUser(postId, request, viewerContext)
                        .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Post flagged successfully"))));

    }
}
