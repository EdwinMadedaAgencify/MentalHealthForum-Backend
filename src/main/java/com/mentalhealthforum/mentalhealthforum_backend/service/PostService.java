package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.AddContentWarningRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.CreatePostRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.PostResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.UpdatePostRequest;
import com.mentalhealthforum.mentalhealthforum_backend.enums.PostType;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PostService {

    // ==================== FUTURE ENHANCEMENTS ====================
// TODO: Add default descriptions to ContentWarningType enum for better UX
// Example: ABUSE("Abuse", "Discusses abusive behavior or harassment")
// Frontend can display description on hover

// TODO: Consider renaming permanentlyDeletePost to purgePost for consistency with ForumCategoryService
// Also consider adding pre-deletion validation (check for dependencies like replies)

    // TODO: Add support for custom text definitions - if user doesn't provide custom text,
// use the enum default description and show tooltip on hover


    Mono<PostResponse> createPost(CreatePostRequest request, ViewerContext viewerContext);

    Mono<PostResponse> getPost(UUID postId, ViewerContext viewerContext);

    Mono<PaginatedResponse<PostResponse>> getAllPosts(
            int page,
            int size,
            UUID threadId,
            UUID authorId,
            UUID parentPostId,
            PostType postType,
            Boolean hasContentWarning,
            Boolean isDeleted,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    );

    Mono<PostResponse> updateOwnPost(UUID postId, UpdatePostRequest request, ViewerContext viewerContext);

    Mono<Void> softDeleteOwnPost(UUID postId, ViewerContext viewerContext);


    // ==================== MODERATOR ACTIONS ====================
    Mono<Void> softDeleteAnyPost(UUID postId, ViewerContext viewerContext);

    Mono<Void> restorePost(UUID postId, ViewerContext viewerContext);

    Mono<PostResponse> addContentWarning(UUID postId, AddContentWarningRequest request, ViewerContext viewerContext);

    // ==================== ADMIN ACTIONS ====================
    Mono<Void> permanentlyDeletePost(UUID postId, ViewerContext viewerContext);
}
