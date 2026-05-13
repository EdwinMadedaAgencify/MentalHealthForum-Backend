package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.CreatePostRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.FlagPostRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.PostResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety.UpdatePostRequest;
import com.mentalhealthforum.mentalhealthforum_backend.enums.PostType;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PostService {
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
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    );

    Mono<PostResponse> updateOwnPost(UUID postId, UpdatePostRequest request, ViewerContext viewerContext);

    Mono<Void> softDeleteOwnPost(UUID postId, ViewerContext viewerContext);

    // ==================== USER FLAG ACTIONS ====================
    Mono<Void> flagPostAsUser(UUID postId, FlagPostRequest request, ViewerContext viewerContext);

    // ==================== MODERATOR ACTIONS ====================
    Mono<Void> softDeleteAnyPost(UUID postId, ViewerContext viewerContext);

    Mono<Void> restorePost(UUID postId, ViewerContext viewerContext);

    Mono<Void> flagPostAsModerator(UUID postId, ViewerContext viewerContext);

    Mono<Void> clearFlag(UUID postId, ViewerContext viewerContext);

    Mono<PaginatedResponse<PostResponse>> getFlaggedPosts(
            int page,
            int size,
            UUID authorId,
            PostType postType,
            Boolean hasContentWarning,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    );

    // ==================== ADMIN ACTIONS ====================
    Mono<Void> permanentlyDeletePost(UUID postId, ViewerContext viewerContext);
}
