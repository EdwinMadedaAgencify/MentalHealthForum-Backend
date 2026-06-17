package com.mentalhealthforum.mentalhealthforum_backend.controller.admin;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.CategoryTagService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
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
@RequestMapping("/api/admin/forum/tags")
public class AdminTagController {

    private final CategoryTagService categoryTagService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public AdminTagController(
            CategoryTagService categoryTagService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.categoryTagService = categoryTagService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== TAG OPERATIONS ====================

    @PostMapping
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryTagResponse>>> createTag(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CategoryTagRequest request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryTagService.createTag(request, viewerContext)
                .map(tag -> ResponseEntity.status(HttpStatus.CREATED).body(
                        new StandardSuccessResponse<>("Tag created successfully", tag)
                ));
    }

    @PutMapping("/{tagId}")
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryTagResponse>>> updateTag(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tagId,
            @Valid @RequestBody CategoryTagRequest request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryTagService.updateTag(tagId, request, viewerContext)
                .map(tag -> ResponseEntity.ok(new StandardSuccessResponse<>("Tag updated successfully", tag)));
    }

    @DeleteMapping
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> deleteTags(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeleteTagRequest request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryTagService.deleteTags(request, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Tags deleted successfully"))));
    }

    // ==================== CATEGORY-TAG ASSIGNMENTS ====================

    @PostMapping("/{tagId}/categories/")
    public Mono<ResponseEntity<StandardSuccessResponse<List<CategoryTagAssignmentResponse>>>> assignTagToCategories(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tagId,
            @Valid @RequestBody AssignTagToCategories request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryTagService.assignTagToCategories(request, tagId, viewerContext)
                .collectList()
                .map(assignments ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Tag assigned to categories successfully", assignments)));
    }


    @DeleteMapping("/{tagId}/categories")
    public Mono<ResponseEntity<StandardSuccessResponse<List<CategoryTagAssignmentResponse>>>> removeTagFromCategories(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tagId,
            @Valid @RequestBody RemoveTagFromCategories request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryTagService.removeTagFromCategories(request, tagId, viewerContext)
                .collectList()
                .map(assignments ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Tag removed from categories", assignments)));
    }

}
