package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface CategoryTagService {

    Mono<CategoryTagResponse> createTag(CategoryTagRequest request, ViewerContext viewerContext);

    Mono<CategoryTagResponse> updateTag(UUID tagId, CategoryTagRequest request, ViewerContext viewerContext);

    Mono<Void> deleteTags(DeleteTagRequest request, ViewerContext viewerContext);

    Mono<CategoryTagResponse> getTagById(UUID tagId);

    Mono<CategoryTagResponse> getTagByName(String name);

    Mono<CategoryTagResponse> getTagBySlug(String name);

    Flux<CategoryTagAssignmentResponse> removeTagFromCategories(RemoveTagFromCategories request, UUID tagId, ViewerContext viewerContext);

    Mono<Void> deleteAllTagAssignmentsForCategory(UUID categoryId, ViewerContext viewerContext);

    Flux<CategoryTagResponse> getTagsForCategory(UUID categoryId);

    Mono<PaginatedResponse<CategoryTagResponse>> getAllTags(int page, int size, String search, String sortBy, String sortDirection);

    Flux<CategoryTagAssignmentResponse> assignTagToCategories(AssignTagToCategories request,UUID tagId, ViewerContext viewerContext);

    Mono<CategoryTagAssignmentResponse> assignTagToCategory(UUID categoryId, UUID tagId, ViewerContext viewerContext);

    Mono<Void> removeTagFromCategory(UUID categoryId, UUID tagId, ViewerContext viewerContext);

    Flux<CategoryTagAssignmentResponse> getAssignmentsForCategory(UUID categoryId);

    Mono<Void> addTagsToCategory(CategoryEntity category, List<UUID> tagIds, ViewerContext viewerContext);

    Mono<Void> syncCategoryTags(UUID id, List<UUID> tagIds, ViewerContext viewerContext);
}
