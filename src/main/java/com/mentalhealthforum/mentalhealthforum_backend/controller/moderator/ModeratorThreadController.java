package com.mentalhealthforum.mentalhealthforum_backend.controller.moderator;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.AddContentWarningRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.ThreadService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/moderator/forum/threads")
public class ModeratorThreadController {

    private final ThreadService threadService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public ModeratorThreadController(
            ThreadService threadService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.threadService = threadService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== MODERATION ACTIONS ====================

    @PatchMapping("/{threadId}/archive")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> archiveThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.archiveThread(threadId, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread archived successfully",thread)));
    }

    @PatchMapping("/{threadId}/unarchive")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> unArchiveThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.unArchiveThread(threadId, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread unarchived successfully",thread)));
    }

    @PatchMapping("/{threadId}/lock")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> lockThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @Valid @RequestBody LockThreadRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.lockThread(threadId, request, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread locked successfully",thread)));
    }

    @PatchMapping("/{threadId}/unlock")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> unlockThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.unlockThread(threadId, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread unlocked successfully",thread)));
    }

    @PatchMapping("/{threadId}/type")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> updateThreadType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @Valid @RequestBody UpdateThreadTypeRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.updateThreadType(threadId, request, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread type updated successfully",thread)));
    }

    @PatchMapping("/{threadId}/sticky")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> toggleSticky(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @RequestParam boolean sticky
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.toggleSticky(threadId, sticky, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>(sticky?
                        "Thread pinned successfully" :
                        "Thread unpinned successfully",thread)));
    }

    @PatchMapping("/{threadId}/featured")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> toggleFeatured(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @RequestParam boolean featured
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.toggleFeatured(threadId, featured, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>(featured?
                        "Thread featured successfully" :
                        "Thread un-featured successfully",thread)));
    }

    @PatchMapping("/{threadId}/move")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> moveThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @RequestParam UUID categoryId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.moveThread(threadId, categoryId, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread moved successfully",thread)));
    }

    @DeleteMapping("/{threadId}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> softDeleteThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.softDeleteThread(threadId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Thread soft deleted successfully"))));
    }

    @PatchMapping("/{threadId}/restore")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> restoreThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.restoreThread(threadId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Thread restored successfully"))));
    }


    @PatchMapping("/{threadId}/content-warning")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> addThreadContentWarning(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @Valid @RequestBody AddContentWarningRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.addThreadContentWarning(threadId, request, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Content warning added successfully",thread)));
    }

    // ==================== BEST ANSWER ====================

    @PatchMapping("/{threadId}/best-answer/{postId}")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> setBestAnswer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @PathVariable UUID postId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.setBestAnswer(threadId, postId, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Best answer set successfully", thread)));
    }

    @DeleteMapping("/{threadId}/best-answer")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> clearBestAnswer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.clearBestAnswer(threadId, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Best answer cleared successfully", thread)));
    }

    // ==================== MERGE / SPLIT ====================

    @PostMapping("/merge")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> mergeThreads(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MergeThreadRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.mergeThreads(request, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Threads merged successfully",thread)));
    }

    @PostMapping("/{threadId}/split")
    public Mono<ResponseEntity<StandardSuccessResponse<ThreadResponse>>> splitThread(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID threadId,
            @Valid @RequestBody SplitThreadRequest request
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return threadService.splitThread(threadId, request, viewerContext)
                .map(thread -> ResponseEntity.ok(new StandardSuccessResponse<>("Thread split successfully",thread)));
    }

}
