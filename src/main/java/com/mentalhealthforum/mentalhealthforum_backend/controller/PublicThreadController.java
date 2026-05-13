package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata.CreateThreadRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata.ThreadResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata.UpdateOwnThreadRequest;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import com.mentalhealthforum.mentalhealthforum_backend.model.ThreadStatusDefinitionEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ThreadTypeDefinitionEntity;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumThreadService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/forum/threads")
public class PublicThreadController {

    private final ForumThreadService forumThreadService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public PublicThreadController(
            ForumThreadService forumThreadService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.forumThreadService = forumThreadService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== CREATE / READ ====================

    @PostMapping
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> createThread(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateThreadRequest request
            ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumThreadService.createThread(request, viewerContext)
                .map(thread -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new StandardSuccessResponse<>("Thread created Successfully", thread)));
    }

    @GetMapping("/{threadId}")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> getThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumThreadService.getThread(threadId, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread retrieved Successfully",thread)));
    }

    // ==================== OP ACTIONS (Creator Only) ====================

    @PutMapping("/{threadId}")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> updateOwnThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @Valid @RequestBody UpdateOwnThreadRequest updateOwnThreadRequest
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumThreadService.updateOwnThread(threadId, updateOwnThreadRequest, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread updated Successfully",thread)));
    }

    @DeleteMapping("/{threadId}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> softOwnDeleteThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumThreadService.softDeleteOwnThread(threadId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Thread soft deleted Successfully"))));
    }

    @PostMapping("/{threadId}/best-answer/{postId}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> setBestAnswerAsOriginalPoster(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @PathVariable UUID postId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumThreadService.setBestAnswerAsOriginalPoster(threadId, postId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Best answer set Successfully"))));
    }

    // ==================== LISTINGS ====================

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<ThreadResponse>>>> getAllThreads(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "category_id") @Parameter(name = "category_id", description = "Filter by category ID") UUID categoryId,
            @RequestParam(required = false, name = "creator_id") @Parameter(name = "creator_id", description = "Filter by creator user ID") UUID creatorId,
            @RequestParam(required = false, name = "thread_type") @Parameter(name = "thread_type", description = "Filter by thread type: DISCUSSION, QUESTION, CRISIS_SUPPORT, PEER_REVIEW, POLL") ThreadType threadType,
            @RequestParam(required = false, name = "thread_status") @Parameter(name = "thread_status", description = "Filter by thread status: OPEN, RESOLVED, CLOSED, ARCHIVED") ThreadStatus threadStatus,
            @RequestParam(defaultValue = "false", name = "is_deleted") @Parameter(name = "is_deleted", description = "Include soft-deleted threads") boolean isDeleted,
            @RequestParam(required = false, name = "is_featured") @Parameter(name = "is_featured", description = "Filter by featured status") Boolean isFeatured,
            @RequestParam(required = false, name = "has_content_warning") @Parameter(name = "has_content_warning", description = "Filter threads with content warnings") Boolean hasContentWarning,
            @RequestParam(defaultValue = "", name = "search") @Parameter(name = "search", description = "Search by title (case-insensitive contains)") String search,
            @RequestParam(defaultValue = "last_activity_at", name = "sort_by") @Parameter(name = "sort_by", description = "Field to sort by: created_at, last_activity_at, post_count, view_count, title") String sortBy,
            @RequestParam(required = false, name = "sort_direction") @Parameter(name = "sort_direction", description = "Sort direction: asc or desc") String sortDirection
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        // forumThreadService.getAllPosts returns Mono<PaginatedResponse<UserRepresentation>>
        return forumThreadService.getAllThreads(page, size, categoryId, creatorId, threadType, threadStatus, isDeleted, isFeatured, hasContentWarning, search, sortBy, sortDirection, viewerContext)
                .map(paginatedThreads -> {
                    String message = "Paginated thread records retrieved successfully.";
                    StandardSuccessResponse<PaginatedResponse<ThreadResponse>> response = new StandardSuccessResponse<>(message, paginatedThreads);
                    return ResponseEntity.ok(response);
                });
    }

    // ==================== REFERENCE DATA ====================

    @GetMapping("/thread_types")
    public Mono<ResponseEntity<StandardSuccessResponse<List<ThreadTypeDefinitionEntity>>>> getThreadTypes(
    ){
        return forumThreadService.getThreadTypes()
                .collectList()
                .map(threadTypes -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread types retrieved Successfully", threadTypes)));
    }

    @GetMapping("/thread_statuses")
    public Mono<ResponseEntity<StandardSuccessResponse<List<ThreadStatusDefinitionEntity>>>> getThreadStatuses(
    ){
        return forumThreadService.getThreadStatuses()
                .collectList()
                .map(threadStatuses -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread statuses retrieved Successfully", threadStatuses)));
    }


}
