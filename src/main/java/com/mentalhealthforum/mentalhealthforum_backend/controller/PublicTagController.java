package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.CategoryTagResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.CategoryTagService;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/forum/tags")
public class PublicTagController {

    private final CategoryTagService categoryTagService;

    public PublicTagController(
            CategoryTagService categoryTagService) {
        this.categoryTagService = categoryTagService;
    }

    // ==================== TAG QUERIES ====================

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<CategoryTagResponse>>>> getAllTags(
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number (0-indexed)") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Number of items per page") int size,
            @RequestParam(defaultValue = "") @Parameter(description = "Search by tag name or description") String search,
            @RequestParam(defaultValue = "name") @Parameter(description = "Sort by: name, created_at, usage") String sortBy,
            @RequestParam(required = false) @Parameter(description = "Sort direction: asc or desc (defaults: name=asc, created_at=desc, usage=desc)") String sortDirection
    ) {
        return categoryTagService.getAllTags(page, size, search, sortBy, sortDirection)
                .map(response ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Tags retrieved successfully", response)));
    }

    @GetMapping("{tagId}")
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryTagResponse>>> getTagById(
            @PathVariable UUID tagId
       ) {
        return categoryTagService.getTagById(tagId)
                .map(tag -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Tag retrieved successfully", tag)
                ));
    }

    @GetMapping("/by-slug/{slug}")
    public Mono<ResponseEntity<StandardSuccessResponse<CategoryTagResponse>>> getTagBySlug(
            @PathVariable String slug
    ) {
        return categoryTagService.getTagBySlug(slug)
                .map(tag -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Tag retrieved successfully", tag)
                ));
    }


    @GetMapping("/categories/{categoryId}")
    public Mono<ResponseEntity<StandardSuccessResponse<List<CategoryTagResponse>>>> getTagsForCategory(
            @PathVariable UUID categoryId) {
        return categoryTagService.getTagsForCategory(categoryId)
                .collectList()
                .map(tags ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Tags retrieved successfully", tags)));
    }



}
