package com.mentalhealthforum.mentalhealthforum_backend.controller.admin;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumCategoryService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/admin/forum/categories")
public class AdminForumCategoryController {

    private final ForumCategoryService forumCategoryService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public AdminForumCategoryController(ForumCategoryService forumCategoryService, JwtClaimsExtractor jwtClaimsExtractor) {
        this.forumCategoryService = forumCategoryService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== UTILITY ENDPOINTS ====================

    @GetMapping("/generate-slug")
    public Mono<ResponseEntity<StandardSuccessResponse<SlugGenerationResponse>>> generateSlug(
            @RequestParam String name,
            @RequestParam(required = false) UUID excludeCategoryId) {
        return forumCategoryService.generateSlug(name, excludeCategoryId)
                .map(response -> ResponseEntity.ok(new StandardSuccessResponse<>(
                                response.available() ? "Slug available" : "Slug not available, suggested alternative",
                                response
                )));
    }

    // ==================== CATEGORY CRUD ====================

    @PostMapping
    public Mono<ResponseEntity<StandardSuccessResponse<ForumCategoryResponse>>> createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateForumCategoryRequest request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.createCategory(request, viewerContext)
                .map(category -> ResponseEntity.ok(new StandardSuccessResponse<>("Category created successfully", category)));
    }

    @GetMapping("/all")
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<ForumCategoryResponse>>>> getAllCategories(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "tag_name") @Parameter(description = "Filter by tag name") String tagName,
            @RequestParam(required = false, name = "parent_category_id") @Parameter(description = "Filter by parent category id") UUID parentCategoryId,
            @RequestParam(required = false, name = "is_parent") @Parameter(description = "Filter by is parent") Boolean isParent,
            @RequestParam(defaultValue = "", name = "search") @Parameter(description = "Search by name, slug, or description") String search,
            @RequestParam(defaultValue = "", name = "is_active") @Parameter(description = "Filter by active") Boolean isActive,
            @RequestParam(defaultValue = "sort_order", name = "sort_by") @Parameter(description = "Sort by: sort_order, name, created_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction") @Parameter(description = "Sort direction: asc or desc") String sortDirection
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.getAllCategories(page, size, tagName, parentCategoryId, isParent, search, isActive, sortBy, sortDirection, viewerContext)
                .map(response -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Categories retrieved successfully", response)));
    }


    @GetMapping("/inactive/count")
    public Mono<ResponseEntity<StandardSuccessResponse<Map<String, Long>>>> getInactiveCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.getInactiveCount(viewerContext)
                .map(count -> ResponseEntity.ok(new StandardSuccessResponse<>(
                                "Inactive categories count retrieved",
                                Map.of("inactiveCount", count)
                )));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<StandardSuccessResponse<ForumCategoryResponse>>> updateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateForumCategoryRequest request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.updateCategory(id, request, viewerContext)
                .map(category -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Category updated successfully", category)));
    }

    @PutMapping("/{id}/reactivate")
    public Mono<ResponseEntity<StandardSuccessResponse<ForumCategoryResponse>>> reactivateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.reactivateCategory(id, viewerContext)
                .map(category -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Category reactivated successfully", category)));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> softDeleteCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.softDeleteCategory(id, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Category soft deleted successfully"))));
    }

    @DeleteMapping("/{id}/purge")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> purgeCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.purgeCategory(id, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Category permanently purged successfully"))));
    }

    @DeleteMapping("/purge-old")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> purgeOldCategories(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "90") int daysOld) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.purgeOldInactiveCategories(daysOld, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>(
                        String.format("Categories inactive for more than %d days purged", daysOld)))));
    }

    // ==================== TAG OPERATIONS ====================

    @PostMapping("/{categoryId}/tags")
    public Mono<ResponseEntity<StandardSuccessResponse<ForumCategoryTagResponse>>> addTag(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId,
            @Valid @RequestBody ForumCategoryTagRequest request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.addTag(categoryId, request, viewerContext)
                .map(tag -> ResponseEntity.ok(new StandardSuccessResponse<>("Tag added successfully", tag)));
    }

    @PutMapping("/{categoryId}/tags/{tagName}")
    public Mono<ResponseEntity<StandardSuccessResponse<ForumCategoryTagResponse>>> updateTagDescription(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId,
            @PathVariable String tagName,
            @RequestParam String description) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.updateTagDescription(categoryId, tagName, description, viewerContext)
                .map(tag -> ResponseEntity.ok(new StandardSuccessResponse<>("Tag description updated successfully", tag)));
    }

    @DeleteMapping("/{categoryId}/tags/{tagName}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> removeTag(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId,
            @PathVariable String tagName) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.removeTag(categoryId, tagName, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Tag removed successfully"))));
    }

    @PutMapping("/{categoryId}/tags/replace")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> replaceTags(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId,
            @RequestBody List<ForumCategoryTagRequest> tags) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return forumCategoryService.replaceTags(categoryId, tags, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Tags replaced successfully"))));
    }
}