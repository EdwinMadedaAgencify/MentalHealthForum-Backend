package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryHierarchyDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryTagResponse;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumCategoryService;
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
public class PublicForumCategoryController {

    private final ForumCategoryService forumCategoryService;

    public PublicForumCategoryController(ForumCategoryService forumCategoryService) {
        this.forumCategoryService = forumCategoryService;
    }

    // ==================== LISTINGS ====================

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<ForumCategoryResponse>>>> getActiveCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "tag_name") @Parameter(description = "Filter by tag name") String tagName,
            @RequestParam(required = false, name = "parent_category_id") @Parameter(description = "Filter by parent category id") UUID parentCategoryId,
            @RequestParam(required = false, name = "is_parent") @Parameter(description = "Filter by is parent") Boolean isParent,
            @RequestParam(defaultValue = "", name = "search") @Parameter(description = "Search by name, slug, or description") String search,
            @RequestParam(defaultValue = "sort_order", name = "sort_by") @Parameter(description = "Sort by: sort_order, name, created_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction") @Parameter(description = "Sort direction: asc or desc") String sortDirection
    ) {
        return forumCategoryService.getActiveCategories(page, size, tagName, parentCategoryId, isParent, search, sortBy, sortDirection)
                .map(response -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Categories retrieved successfully", response)));
    }

    // ==================== HIERARCHY ENDPOINTS ====================

    @GetMapping("/hierarchy")
    public Mono<ResponseEntity<StandardSuccessResponse<List<ForumCategoryHierarchyDto>>>> getCategoryHierarchy() {
        return forumCategoryService.getCategoryHierarchy()
                .collectList()
                .map(hierarchy -> ResponseEntity.ok(new StandardSuccessResponse<>("Hierarchy retrieved", hierarchy)));
    }

    @GetMapping("/root")
    public Mono<ResponseEntity<StandardSuccessResponse<List<ForumCategoryResponse>>>> getRootCategories() {
        return forumCategoryService.getRootCategories()
                .collectList()
                .map(categories -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Root categories retrieved successfully", categories)));
    }

    @GetMapping("/{parentId}/children")
    public Mono<ResponseEntity<StandardSuccessResponse<List<ForumCategoryResponse>>>> getChildCategories(@PathVariable UUID parentId) {
        return forumCategoryService.getChildCategories(parentId)
                .collectList()
                .map(categories -> ResponseEntity.ok(new StandardSuccessResponse<>(
                        "Child categories retrieved successfully", categories)));
    }

    // ==================== SINGLE CATEGORY ====================

    @GetMapping("/{id}")
    public Mono<ResponseEntity<StandardSuccessResponse<ForumCategoryResponse>>> getCategoryById(@PathVariable UUID id) {
        return forumCategoryService.getCategoryById(id)
                .map(category -> ResponseEntity.ok(new StandardSuccessResponse<>("Category found", category)));
    }

    @GetMapping("/slug/{slug}")
    public Mono<ResponseEntity<StandardSuccessResponse<ForumCategoryResponse>>> getCategoryBySlug(@PathVariable String slug) {
        return forumCategoryService.getCategoryBySlug(slug)
                .map(category -> ResponseEntity.ok(new StandardSuccessResponse<>("Category found", category)));
    }


    // ==================== TAG ENDPOINTS ====================

    @GetMapping("/{categoryId}/tags")
    public Mono<ResponseEntity<StandardSuccessResponse<List<ForumCategoryTagResponse>>>> getTags(@PathVariable UUID categoryId) {
        return forumCategoryService.getTags(categoryId)
                .collectList()
                .map(tags -> ResponseEntity.ok(new StandardSuccessResponse<>("Tags retrieved successfully", tags)));
    }

}