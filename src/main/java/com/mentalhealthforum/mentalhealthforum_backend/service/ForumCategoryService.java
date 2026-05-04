package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.CreateForumCategoryRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryHierarchyDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryTagRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.UpdateForumCategoryRequest;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface ForumCategoryService {

    // Slug generation
    Mono<SlugGenerationResponse> generateSlug(String name, UUID excludeCategoryId);

    // Category CRUD
    Mono<ForumCategoryEntity> createCategory(CreateForumCategoryRequest request);
    Mono<ForumCategoryEntity> updateCategory(UUID id, UpdateForumCategoryRequest request);
    Mono<Void> deleteCategory(UUID id);
    Mono<ForumCategoryEntity> getCategoryById(UUID id);
    Mono<ForumCategoryEntity> getCategoryBySlug(String slug);
    Flux<ForumCategoryEntity> getAllActiveCategories();

    // Hierarchy
    Flux<ForumCategoryHierarchyDto> getCategoryHierarchy();
    Flux<ForumCategoryEntity> getRootCategories();
    Flux<ForumCategoryEntity> getChildCategories(UUID parentId);

    // Tag operations
    Flux<ForumCategoryTagEntity> getTags(UUID categoryId);
    Mono<ForumCategoryTagEntity> addTag(UUID categoryId, ForumCategoryTagRequest request);
    Mono<ForumCategoryTagEntity> updateTagDescription(UUID categoryId, String tagName, String description);
    Mono<Void> removeTag(UUID categoryId, String tagName);
    Mono<Void> replaceTags(UUID categoryId, List<ForumCategoryTagRequest> tags);
}
