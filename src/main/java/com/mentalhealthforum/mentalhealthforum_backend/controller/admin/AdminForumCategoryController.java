package com.mentalhealthforum.mentalhealthforum_backend.controller.admin;

import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.CreateForumCategoryRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryTagRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.UpdateForumCategoryRequest;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumCategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/admin/forum/categories")
public class AdminForumCategoryController {

    private final ForumCategoryService forumCategoryService;

    public AdminForumCategoryController(ForumCategoryService forumCategoryService) {
        this.forumCategoryService = forumCategoryService;
    }

    // ==================== UTILITY ENDPOINTS ====================

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

    // ==================== CATEGORY CRUD ====================

    @PostMapping
    public Mono<StandardSuccessResponse<ForumCategoryEntity>> createCategory(
            @Valid @RequestBody CreateForumCategoryRequest request) {
        return forumCategoryService.createCategory(request)
                .map(category -> new StandardSuccessResponse<>("Category created successfully", category));
    }

    @GetMapping("/all")
    public Mono<StandardSuccessResponse<List<ForumCategoryEntity>>> getAllCategories() {
        return forumCategoryService.getAllCategories()
                .collectList()
                .map(categories -> new StandardSuccessResponse<>("All categories retrieved", categories));
    }

    @GetMapping
    public Mono<StandardSuccessResponse<List<ForumCategoryEntity>>> getAllActiveCategories() {
        return forumCategoryService.getAllActiveCategories()
                .collectList()
                .map(categories -> new StandardSuccessResponse<>("Active categories retrieved", categories));
    }

    @GetMapping("/inactive")
    public Mono<StandardSuccessResponse<List<ForumCategoryEntity>>> getInactiveCategories() {
        return forumCategoryService.getInactiveCategories()
                .collectList()
                .map(categories -> new StandardSuccessResponse<>("Inactive categories retrieved", categories));
    }

    @GetMapping("/inactive/count")
    public Mono<StandardSuccessResponse<Map<String, Long>>> getInactiveCount() {
        return forumCategoryService.getInactiveCount()
                .map(count -> new StandardSuccessResponse<>(
                        "Inactive categories count retrieved",
                        Map.of("inactiveCount", count)
                ));
    }

    @PutMapping("/{id}")
    public Mono<StandardSuccessResponse<ForumCategoryEntity>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateForumCategoryRequest request) {
        return forumCategoryService.updateCategory(id, request)
                .map(category -> new StandardSuccessResponse<>("Category updated successfully", category));
    }

    @PutMapping("/{id}/reactivate")
    public Mono<StandardSuccessResponse<ForumCategoryEntity>> reactivateCategory(@PathVariable UUID id) {
        return forumCategoryService.reactivateCategory(id)
                .map(category -> new StandardSuccessResponse<>("Category reactivated successfully", category));
    }

    @DeleteMapping("/{id}")
    public Mono<StandardSuccessResponse<Void>> softDeleteCategory(@PathVariable UUID id) {
        return forumCategoryService.softDeleteCategory(id)
                .then(Mono.just(new StandardSuccessResponse<>("Category soft deleted successfully")));
    }

    @DeleteMapping("/{id}/purge")
    public Mono<StandardSuccessResponse<Void>> purgeCategory(@PathVariable UUID id) {
        return forumCategoryService.purgeCategory(id)
                .then(Mono.just(new StandardSuccessResponse<>("Category permanently purged successfully")));
    }

    @DeleteMapping("/purge-old")
    public Mono<StandardSuccessResponse<Void>> purgeOldCategories(@RequestParam(defaultValue = "90") int daysOld) {
        return forumCategoryService.purgeOldInactiveCategories(daysOld)
                .then(Mono.just(new StandardSuccessResponse<>(
                        String.format("Categories inactive for more than %d days purged", daysOld))));
    }

    // ==================== TAG ENDPOINTS ====================

    @GetMapping("/{categoryId}/tags")
    public Mono<StandardSuccessResponse<List<ForumCategoryTagEntity>>> getTags(@PathVariable UUID categoryId) {
        return forumCategoryService.getTags(categoryId)
                .collectList()
                .map(tags -> new StandardSuccessResponse<>("Tags retrieved successfully", tags));
    }

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
}