package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.CategoryHierarchyDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.CategoryResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.CategoryService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/forum/categories")
public class PublicCategoryController {

    private final CategoryService categoryService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public PublicCategoryController(
            CategoryService categoryService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.categoryService = categoryService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== LISTINGS ====================

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<CategoryResponse>>>> getActiveCategories(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "tag_id") @Parameter(description = "Filter by tag id") UUID tagId,
            @RequestParam(required = false, name = "parent_category_id") @Parameter(description = "Filter by parent category id") UUID parentCategoryId,
            @RequestParam(required = false, name = "is_parent") @Parameter(description = "Filter by is parent") Boolean isParent,
            @RequestParam(required = false, name = "is_focused") @Parameter(description = "Filter by focus") Boolean isFocused,
            @RequestParam(defaultValue = "", name = "search") @Parameter(description = "Search by name, slug, or description") String search,
            @RequestParam(defaultValue = "sort_order", name = "sort_by") @Parameter(description = "Sort by: sort_order, name, created_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction") @Parameter(description = "Sort direction: asc or desc") String sortDirection
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.getActiveCategories(page, size, tagId, parentCategoryId, isParent, isFocused, search, sortBy, sortDirection, viewerContext)
                .map(response -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Categories retrieved successfully", response)));
    }

    // ==================== HIERARCHY ENDPOINTS ====================

    @GetMapping("/hierarchy")
    public Mono<ResponseEntity<StandardSuccessResponse<List<CategoryHierarchyDto>>>> getCategoryHierarchy(@AuthenticationPrincipal Jwt jwt) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.getCategoryHierarchy(viewerContext)
                .collectList()
                .map(hierarchy -> ResponseEntity.ok(new StandardSuccessResponse<>("Hierarchy retrieved", hierarchy)));
    }

    @GetMapping("/root")
    public Mono<ResponseEntity<StandardSuccessResponse<List<CategoryResponse>>>> getRootCategories(@AuthenticationPrincipal Jwt jwt) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.getRootCategories(viewerContext)
                .collectList()
                .map(categories -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Root categories retrieved successfully", categories)));
    }

    @GetMapping("/{parentId}/children")
    public Mono<ResponseEntity<StandardSuccessResponse<List<CategoryResponse>>>> getChildCategories(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID parentId) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.getChildCategories(parentId, viewerContext)
                .collectList()
                .map(categories -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Child categories retrieved successfully", categories)));
    }

    // ==================== SINGLE CATEGORY ====================

    @GetMapping("/{categoryId}")
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryResponse>>> getCategoryById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.getCategoryById(categoryId, viewerContext)
                .map(category -> ResponseEntity.ok(new StandardSuccessResponse<>("Category found", category)));
    }

    @GetMapping("/slug/{slug}")
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryResponse>>> getCategoryBySlug(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String slug) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.getCategoryBySlug(slug, viewerContext)
                .map(category -> ResponseEntity.ok(new StandardSuccessResponse<>("Category found", category)));
    }

}