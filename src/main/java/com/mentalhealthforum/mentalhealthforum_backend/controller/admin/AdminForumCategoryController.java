package com.mentalhealthforum.mentalhealthforum_backend.controller.admin;

import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.CreateForumCategoryRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryHierarchyDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryTagRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.UpdateForumCategoryRequest;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumCategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/admin/forum/categories")
public class AdminForumCategoryController {

    private final ForumCategoryService forumCategoryService;

    public AdminForumCategoryController(ForumCategoryService forumCategoryService) {
        this.forumCategoryService = forumCategoryService;
    }

    // ==================== CATEGORY ENDPOINTS ====================

    @PostMapping
    public Mono<StandardSuccessResponse<ForumCategoryEntity>> createCategory(
            @Valid @RequestBody CreateForumCategoryRequest request) {
        return forumCategoryService.createCategory(request)
                .map(category -> new StandardSuccessResponse<>("Category created successfully", category));
    }

    @PutMapping("/{id}")
    public Mono<StandardSuccessResponse<ForumCategoryEntity>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateForumCategoryRequest request) {
        return forumCategoryService.updateCategory(id, request)
                .map(category -> new StandardSuccessResponse<>("Category updated successfully", category));
    }

    @DeleteMapping("/{id}")
    public Mono<StandardSuccessResponse<Void>> deleteCategory(@PathVariable UUID id) {
        return forumCategoryService.deleteCategory(id)
                .then(Mono.just(new StandardSuccessResponse<>("Category deleted successfully")));
    }


    // ==================== TAG ENDPOINTS ====================

    @PostMapping("/{categoryId}/tags")
    public Mono<StandardSuccessResponse<ForumCategoryTagEntity>> addTag(
            @PathVariable UUID categoryId,
            @Valid @RequestBody ForumCategoryTagRequest request) {
        return forumCategoryService.addTag(categoryId, request)
                .map(tag -> new StandardSuccessResponse<>("Tag added successfully", tag));
    }

    @PutMapping("/{categoryId}/tags/{tagName}")
    public Mono<StandardSuccessResponse<ForumCategoryTagEntity>> updateTagDescription(
            @PathVariable UUID categoryId,
            @PathVariable String tagName,
            @RequestParam String description) {
        return forumCategoryService.updateTagDescription(categoryId, tagName, description)
                .map(tag -> new StandardSuccessResponse<>("Tag description updated successfully", tag));
    }

    @DeleteMapping("/{categoryId}/tags/{tagName}")
    public Mono<StandardSuccessResponse<Void>> removeTag(
            @PathVariable UUID categoryId,
            @PathVariable String tagName) {
        return forumCategoryService.removeTag(categoryId, tagName)
                .then(Mono.just(new StandardSuccessResponse<>("Tag removed successfully")));
    }

    @PutMapping("/{categoryId}/tags/replace")
    public Mono<StandardSuccessResponse<Void>> replaceTags(
            @PathVariable UUID categoryId,
            @RequestBody List<ForumCategoryTagRequest> tags) {
        return forumCategoryService.replaceTags(categoryId, tags)
                .then(Mono.just(new StandardSuccessResponse<>("Tags replaced successfully")));
    }

    @GetMapping("/generate-slug")
    public Mono<StandardSuccessResponse<SlugGenerationResponse>> generateSlug(
            @RequestParam String name,
            @RequestParam(required = false) UUID excludeCategoryId) {
        return forumCategoryService.generateSlug(name, excludeCategoryId)
                .map(response -> new StandardSuccessResponse<>(
                        response.available() ? "Slug available" : "Slug not available, suggested alternative",
                        response
                ));
    }
}