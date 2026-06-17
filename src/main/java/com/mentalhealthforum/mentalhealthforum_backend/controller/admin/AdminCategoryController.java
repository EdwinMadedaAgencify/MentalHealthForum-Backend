package com.mentalhealthforum.mentalhealthforum_backend.controller.admin;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.CategoryService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/admin/forum/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public AdminCategoryController(CategoryService categoryService, JwtClaimsExtractor jwtClaimsExtractor) {
        this.categoryService = categoryService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== SLUG GENERATION ====================

    @GetMapping("/generate-slug")
    public Mono<ResponseEntity<StandardSuccessResponse<SlugGenerationResponse>>> generateSlug(
            @RequestParam String name,
            @RequestParam(required = false) UUID excludeTagId) {
        return categoryService.generateCategorySlug(name, excludeTagId)
                .map(response -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        response.available() ? "Slug available" : "Slug not available, suggested alternative",
                        response
                )));
    }

    // ==================== CATEGORY CRUD ====================

    @PostMapping
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryResponse>>> createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCategoryRequest request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.createCategory(request, viewerContext)
                .map(category -> ResponseEntity.ok(new StandardSuccessResponse<>("Category created successfully", category)));
    }

    @GetMapping("/all")
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<CategoryResponse>>>> getAllCategories(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "tag_id") @Parameter(description = "Filter by tag id") UUID tagId,
            @RequestParam(required = false, name = "parent_category_id") @Parameter(description = "Filter by parent category id") UUID parentCategoryId,
            @RequestParam(required = false, name = "is_parent") @Parameter(description = "Filter by is parent") Boolean isParent,
            @RequestParam(required = false, name = "is_active") @Parameter(description = "Filter by active") Boolean isActive,
            @RequestParam(defaultValue = "", name = "search") @Parameter(description = "Search by name, slug, or description") String search,
            @RequestParam(defaultValue = "sort_order", name = "sort_by") @Parameter(description = "Sort by: sort_order, name, created_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction") @Parameter(description = "Sort direction: asc or desc") String sortDirection
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.getAllCategories(page, size, tagId, parentCategoryId, isParent, isActive, search, sortBy, sortDirection, viewerContext)
                .map(response -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Categories retrieved successfully", response)));
    }


    @GetMapping("/inactive/count")
    public Mono<ResponseEntity<StandardSuccessResponse<Map<String, Long>>>> getInactiveCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.getInactiveCount(viewerContext)
                .map(count -> ResponseEntity.ok(new StandardSuccessResponse<>(
                                "Inactive categories count retrieved",
                                Map.of("inactiveCount", count)
                )));
    }

    @PutMapping("/{categoryId}")
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryResponse>>> updateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.updateCategory(categoryId, request, viewerContext)
                .map(category -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Category updated successfully", category)));
    }

    @PutMapping("/{categoryId}/reactivate")
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryResponse>>> reactivateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.reactivateCategory(categoryId, viewerContext)
                .map(category -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Category reactivated successfully", category)));
    }

    @DeleteMapping("/{categoryId}")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> softDeleteCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.softDeleteCategory(categoryId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Category soft deleted successfully"))));
    }

    @DeleteMapping("/{categoryId}/purge")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> purgeCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.purgeCategory(categoryId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>("Category permanently purged successfully"))));
    }

    @DeleteMapping("/purge-old")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> purgeOldCategories(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "90") int daysOld) {
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return categoryService.purgeOldInactiveCategories(daysOld, viewerContext)
                .then(Mono.just(ResponseEntity.ok(new StandardSuccessResponse<>(
                        String.format("Categories inactive for more than %d days purged", daysOld)))));
    }

}