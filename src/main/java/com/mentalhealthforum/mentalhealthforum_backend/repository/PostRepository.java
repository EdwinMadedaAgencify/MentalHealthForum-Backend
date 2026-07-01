package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.PostEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostRepository extends R2dbcRepository<PostEntity, UUID> {

    // ==================== BASIC QUERIES ====================

    Flux<PostEntity> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    Flux<PostEntity> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    Mono<PostEntity> findByIdAndIsDeletedFalse(UUID postId);

    // ==================== PAGINATED QUERIES ====================

    @Query("""
            SELECT * FROM forum_posts p
            INNER JOIN forum_threads t ON p.thread_id = t.id
            INNER JOIN forum_categories c ON t.category_id = c.id
            WHERE (:threadId IS NULL OR p.thread_id = :threadId::uuid)
                AND (:authorId IS NULL OR p.author_id = :authorId::uuid)
                AND (:flaggedForReview IS NULL OR p.flagged_for_review = :flaggedForReview)
                AND (:parentPostId IS NULL OR p.parent_post_id = :parentPostId::uuid)
                AND (:postType IS NULL OR p.post_type = :postType::post_type_enum)
                AND (:hasContentWarning IS NULL OR
                    (CASE WHEN :hasContentWarning = true
                          THEN p.content_warning_type != 'NONE'
                          ELSE p.content_warning_type = 'NONE' END))
    
                AND (:search IS NULL
                        OR to_tsvector('public.english_unaccent', coalesce(p.content, ''))
                            @@ websearch_to_tsquery('public.english_unaccent', :search)
   
                        OR public.unaccent_immutable(p.content) % public.unaccent_immutable(:search)
                )
    
                -- Category visibility
                AND c.is_active = TRUE
                AND (
    
                     (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'PUBLIC')
                     OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MEMBERS_ONLY' AND :viewerId IS NOT NULL)
                     OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'VERIFIED_ONLY' AND :isVerified = TRUE)
                     OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MODERATORS_ONLY' AND :isModeratorOrAdmin = TRUE)
                     OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'ADMINS_ONLY' AND :isAdmin = TRUE)
    
                )
    
                AND p.is_deleted = :isDeleted
                ORDER BY
                    -- 1. Sort by selected field and direction: DESC
                    CASE :sortDirection
                        WHEN 'DESC' THEN
                            CASE :sortBy
                                WHEN 'created_at' THEN p.created_at::text
                                WHEN 'updated_at' THEN p.updated_at::text
                                ELSE p.created_at::text
                            END
                        ELSE NULL
                    END DESC NULLS LAST,
    
                    -- 2. Sort by selected field and direction: ASC
                    CASE :sortDirection
                        WHEN 'ASC' THEN
                            CASE :sortBy
                                WHEN 'created_at' THEN p.created_at::text
                                WHEN 'updated_at' THEN p.updated_at::text
                                ELSE p.created_at::text
                            END
                        ELSE NULL
                    END ASC NULLS FIRST,
    
                    -- 3. Tie breaker for deterministic ordering
                    p.id
                LIMIT :limit OFFSET :offset
    """)
    Flux<PostEntity> findPostsPaginated(
            @Param("viewerId") UUID viewerId,
            @Param("isAdmin") boolean isAdmin,
            @Param("isModeratorOrAdmin") boolean isModeratorOrAdmin,
            @Param("isVerified") boolean isVerified,
            @Param("threadId") UUID threadId,
            @Param("authorId") UUID authorId,
            @Param("parentPostId") UUID parentPostId,
            @Param("postType") String postType,
            @Param("hasContentWarning") Boolean hasContentWarning,
            @Param("isDeleted") Boolean isDeleted,
            @Param("flaggedForReview") Boolean flaggedForReview,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
            SELECT COUNT(*) FROM forum_posts p
            INNER JOIN forum_threads t ON p.thread_id = t.id
            INNER JOIN forum_categories c ON t.category_id = c.id
            WHERE (:threadId IS NULL OR p.thread_id = :threadId::uuid)
                AND (:authorId IS NULL OR p.author_id = :authorId::uuid)
                AND (:flaggedForReview IS NULL OR p.flagged_for_review = :flaggedForReview)
                AND (:parentPostId IS NULL OR p.parent_post_id = :parentPostId::uuid)
                AND (:postType IS NULL OR p.post_type = :postType::post_type_enum)
                AND (:hasContentWarning IS NULL OR
                    (CASE WHEN :hasContentWarning = true
                          THEN p.content_warning_type != 'NONE'
                          ELSE p.content_warning_type = 'NONE' END))

                AND (:search IS NULL
                        OR to_tsvector('public.english_unaccent', coalesce(p.content, ''))
                            @@ websearch_to_tsquery('public.english_unaccent', :search)
   
                        OR public.unaccent_immutable(p.content) % public.unaccent_immutable(:search)
                )
    
                -- Category visibility
                AND c.is_active = TRUE
                AND (
    
                     (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'PUBLIC')
                     OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MEMBERS_ONLY' AND :viewerId IS NOT NULL)
                     OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'VERIFIED_ONLY' AND :isVerified = TRUE)
                     OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MODERATORS_ONLY' AND :isModeratorOrAdmin = TRUE)
                     OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'ADMINS_ONLY' AND :isAdmin = TRUE)
    
                )
 
                AND p.is_deleted = :isDeleted
    """)
    Mono<Long> countPostsWithFilters(
            @Param("viewerId") UUID viewerId,
            @Param("isAdmin") boolean isAdmin,
            @Param("isModeratorOrAdmin") boolean isModeratorOrAdmin,
            @Param("isVerified") boolean isVerified,
            @Param("threadId") UUID threadId,
            @Param("authorId") UUID authorId,
            @Param("parentPostId") UUID parentPostId,
            @Param("postType") String postType,
            @Param("hasContentWarning") Boolean hasContentWarning,
            @Param("isDeleted") Boolean isDeleted,
            @Param("flaggedForReview") Boolean flaggedForReview,
            @Param("search") String search
    );


    // ==================== SOFT DELETE ====================

    @Query("UPDATE forum_posts SET is_deleted = true, updated_at = NOW() WHERE id = :postId")
    Mono<Void> softDeletePost(@Param("postId") UUID postId);

    @Query("UPDATE forum_posts SET is_deleted = false, updated_at = NOW() WHERE id = :postId")
    Mono<Void> restorePost(@Param("postId") UUID postId);

    // ==================== FLAG OPERATIONS ====================

    @Query("UPDATE forum_posts SET flagged_for_review = true, updated_at = NOW() WHERE id = :postId")
    Mono<Void> flagPost(@Param("postId") UUID postId);

    @Query("UPDATE forum_posts SET flagged_for_review = false, updated_at = NOW() WHERE id = :postId")
    Mono<Void> clearFlag(@Param("postId") UUID postId);

    // ==================== EXISTENCE CHECKS ====================

    Mono<Boolean> existsByIdAndIsDeletedFalse(UUID id);

    @Query("""
        SELECT EXISTS(SELECT 1 FROM forum_posts WHERE thread_id = :threadId AND id = :postId)
    """)
    Mono<Boolean> existsByThreadIdAndId(@Param("threadId") UUID threadId, @Param("postId") UUID postId);

    /**
     * Checks if a user is part of a thread discussion.
     * Returns TRUE if the user is either:
     * 1. The thread creator, OR
     * 2. Has posted in the thread
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM forum_threads WHERE id = :threadId AND creator_id = :userId
            UNION
            SELECT 1 FROM forum_posts WHERE thread_id = :threadId AND author_id = :userId
        )
    """)
    Mono<Boolean> isUserInThread(@Param("threadId") UUID threadId, @Param("userId") UUID userId);


    // ==================== FIRST POST IN THREAD ====================

    @Query("""
        SELECT EXISTS(SELECT 1 FROM forum_posts WHERE thread_id = :threadId LIMIT 1)
    """)
    Mono<Boolean> hasAnyPostsInThread(@Param("threadId") UUID threadId);

    @Query("""
        SELECT * FROM forum_posts
        WHERE thread_id = :threadId
        ORDER BY created_at ASC
        LIMIT 1
    """)
    Mono<PostEntity> findFirstPostInThread(@Param("threadId") UUID threadId);


    // ==================== THREAD ACTIONS ====================\

    @Query("SELECT COUNT(*) FROM forum_posts WHERE id IN (:postIds)")
    Mono<Long> countPostsInIds(@Param("postIds") List<UUID> postIds);

    @Query("SELECT COUNT(*) FROM forum_posts WHERE id IN (:postIds) AND thread_id = :threadId AND is_deleted = false")
    Mono<Long> countPostsInIdsAndThread(@Param("postIds") List<UUID> postIds,@Param("threadId") UUID threadId);

    @Query("UPDATE forum_posts SET thread_id = :newThreadId WHERE id IN (:postIds)")
    Mono<Void> movePostsToThread(@Param("postIds") List<UUID> postIds,@Param("newThreadId") UUID newThreadId);

    @Query("UPDATE forum_posts SET thread_id = :targetThreadId WHERE thread_id = :sourceThreadId")
    Mono<Void> moveAllPostsToThread(@Param("sourceThreadId") UUID sourceThreadId,@Param("targetThreadId") UUID targetThreadId);

}
