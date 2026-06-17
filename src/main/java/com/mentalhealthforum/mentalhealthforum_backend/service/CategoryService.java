package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CategoryService {

    Mono<SlugGenerationResponse> generateCategorySlug(String name, UUID excludeTagId);

    // Category CRUD
    Mono<CategoryResponse> createCategory(CreateCategoryRequest request, ViewerContext viewerContext);

    Mono<CategoryResponse> updateCategory(UUID id, UpdateCategoryRequest request, ViewerContext viewerContext);

    Mono<Void> softDeleteCategory(UUID id, ViewerContext viewerContext);

    Mono<CategoryResponse> reactivateCategory(UUID id, ViewerContext viewerContext);

    Mono<Void> purgeCategory(UUID id, ViewerContext viewerContext);

    Mono<Void> purgeOldInactiveCategories(int daysOld, ViewerContext viewerContext);

    Mono<Void> purgeOldInactiveCategoriesInternal(int daysOld);

    Mono<CategoryResponse> getCategoryById(UUID id, ViewerContext viewerContext);

    Mono<CategoryResponse> getCategoryBySlug(String slug, ViewerContext viewerContext);


    Mono<PaginatedResponse<CategoryResponse>> getActiveCategories(
            int page,
            int size,
            UUID tagId,
            UUID parentCategoryId,
            Boolean isParent,
            Boolean isFocused,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext);


    Mono<PaginatedResponse<CategoryResponse>> getAllCategories(
            int page,
            int size,
            UUID tagId,
            UUID parentCategoryId,
            Boolean isParent,
            Boolean isActive,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    );

    Mono<Long> getInactiveCount(ViewerContext viewerContext);

    // Hierarchy

    Flux<CategoryHierarchyDto> getCategoryHierarchy(ViewerContext viewerContext);

    Flux<CategoryResponse> getRootCategories(ViewerContext viewerContext);

    Flux<CategoryResponse> getChildCategories(UUID parentId, ViewerContext viewerContext);

}