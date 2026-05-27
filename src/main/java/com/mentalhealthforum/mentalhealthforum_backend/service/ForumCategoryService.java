package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface ForumCategoryService {
    // Slug generation
    Mono<SlugGenerationResponse> generateSlug(String name, UUID excludeCategoryId);

    // Category CRUD
    Mono<ForumCategoryResponse> createCategory(CreateForumCategoryRequest request, ViewerContext viewerContext);

    Mono<ForumCategoryResponse> updateCategory(UUID id, UpdateForumCategoryRequest request, ViewerContext viewerContext);

    Mono<Void> softDeleteCategory(UUID id, ViewerContext viewerContext);

    Mono<ForumCategoryResponse> reactivateCategory(UUID id, ViewerContext viewerContext);

    Mono<Void> purgeCategory(UUID id, ViewerContext viewerContext);

    Mono<Void> purgeOldInactiveCategories(int daysOld, ViewerContext viewerContext);

    Mono<Void> purgeOldInactiveCategoriesInternal(int daysOld);

    Mono<ForumCategoryResponse> getCategoryById(UUID id);

    Mono<ForumCategoryResponse> getCategoryBySlug(String slug);


    Mono<PaginatedResponse<ForumCategoryResponse>> getActiveCategories(
            int page,
            int size,
            String tagName,
            UUID parentCategoryId,
            Boolean isParent,
            String search,
            String sortBy,
            String sortDirection
    );


    Mono<PaginatedResponse<ForumCategoryResponse>> getAllCategories(
            int page,
            int size,
            String tagName,
            UUID parentCategoryId,
            Boolean isParent,
            String search,
            Boolean isActive,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    );

    Mono<Long> getInactiveCount(ViewerContext viewerContext);

    // Hierarchy

    Flux<ForumCategoryHierarchyDto> getCategoryHierarchy();

    Flux<ForumCategoryResponse> getRootCategories();

    Flux<ForumCategoryResponse> getChildCategories(UUID parentId);


    // Tag operations


    Flux<ForumCategoryTagResponse> getTags(UUID categoryId);

    Mono<ForumCategoryTagResponse> addTag(UUID categoryId, ForumCategoryTagRequest request, ViewerContext viewerContext);

    Mono<ForumCategoryTagResponse> updateTagDescription(UUID categoryId, String tagName, String description, ViewerContext viewerContext);

    Mono<Void> removeTag(UUID categoryId, String tagName, ViewerContext viewerContext);

    Mono<Void> replaceTags(UUID categoryId, List<ForumCategoryTagRequest> tags, ViewerContext viewerContext);


}