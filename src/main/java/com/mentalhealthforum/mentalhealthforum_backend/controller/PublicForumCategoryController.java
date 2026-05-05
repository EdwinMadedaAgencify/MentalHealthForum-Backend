package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryHierarchyDto;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumCategoryService;
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

    // ==================== CATEGORY QUERIES ====================

    @GetMapping
    public Mono<StandardSuccessResponse<List<ForumCategoryEntity>>> getAllActiveCategories() {
        return forumCategoryService.getAllActiveCategories()
                .collectList()
                .map(categories -> new StandardSuccessResponse<>("Categories retrieved", categories));
    }

    @GetMapping("/{id}")
    public Mono<StandardSuccessResponse<ForumCategoryEntity>> getCategoryById(@PathVariable UUID id) {
        return forumCategoryService.getCategoryById(id)
                .map(category -> new StandardSuccessResponse<>("Category found", category));
    }

    @GetMapping("/slug/{slug}")
    public Mono<StandardSuccessResponse<ForumCategoryEntity>> getCategoryBySlug(@PathVariable String slug) {
        return forumCategoryService.getCategoryBySlug(slug)
                .map(category -> new StandardSuccessResponse<>("Category found", category));
    }

    // ==================== HIERARCHY ENDPOINTS ====================

    @GetMapping("/hierarchy")
    public Mono<StandardSuccessResponse<List<ForumCategoryHierarchyDto>>> getCategoryHierarchy() {
        return forumCategoryService.getCategoryHierarchy()
                .collectList()
                .map(hierarchy -> new StandardSuccessResponse<>("Hierarchy retrieved", hierarchy));
    }

    @GetMapping("/root")
    public Mono<StandardSuccessResponse<List<ForumCategoryEntity>>> getRootCategories() {
        return forumCategoryService.getRootCategories()
                .collectList()
                .map(categories -> new StandardSuccessResponse<>("Root categories retrieved successfully", categories));
    }

    @GetMapping("/{parentId}/children")
    public Mono<StandardSuccessResponse<List<ForumCategoryEntity>>> getChildCategories(@PathVariable UUID parentId) {
        return forumCategoryService.getChildCategories(parentId)
                .collectList()
                .map(categories -> new StandardSuccessResponse<>("Child categories retrieved successfully", categories));
    }

    // ==================== TAG ENDPOINTS ====================

    @GetMapping("/{categoryId}/tags")
    public Mono<StandardSuccessResponse<List<ForumCategoryTagEntity>>> getTags(@PathVariable UUID categoryId) {
        return forumCategoryService.getTags(categoryId)
                .collectList()
                .map(tags -> new StandardSuccessResponse<>("Tags retrieved successfully", tags));
    }
}