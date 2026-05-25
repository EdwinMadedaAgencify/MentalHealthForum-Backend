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
            SELECT * FROM forum_posts
            WHERE (:threadId IS NULL OR thread_id = :threadId::uuid)
                AND (:authorId IS NULL OR author_id = :authorId::uuid)
                AND (:flaggedForReview IS NULL OR flagged_for_review = :flaggedForReview)
                AND (:parentPostId IS NULL OR parent_post_id = :parentPostId::uuid)
                AND (:postType IS NULL OR post_type = :postType::post_type_enum)
                AND (:hasContentWarning IS NULL OR
                    (CASE WHEN :hasContentWarning = true
                          THEN content_warning_type != 'NONE'
                          ELSE content_warning_type = 'NONE' END))
                AND (:search IS NULL OR
                    LOWER(content) LIKE '%' || LOWER(:search) || '%')
                AND is_deleted = :isDeleted
                ORDER BY
                    -- 1. Sort by selected field and direction: DESC
                    CASE :sortDirection
                        WHEN 'DESC' THEN
                            CASE :sortBy
                                WHEN 'created_at' THEN created_at::text
                                WHEN 'updated_at' THEN updated_at::text
                                ELSE created_at::text
                            END
                        ELSE NULL
                    END DESC NULLS LAST,
    
                    -- 2. Sort by selected field and direction: ASC
                    CASE :sortDirection
                        WHEN 'ASC' THEN
                            CASE :sortBy
                                WHEN 'created_at' THEN created_at::text
                                WHEN 'updated_at' THEN updated_at::text
                                ELSE created_at::text
                            END
                        ELSE NULL
                    END ASC NULLS FIRST,
    
                    -- 3. Tie breaker for deterministic ordering
                    id
                LIMIT :limit OFFSET :offset
    """)
    Flux<PostEntity> findPostsPaginated(
            @Param("threadId") UUID threadId,
            @Param("authorId") UUID authorId,
            @Param("flaggedForReview") Boolean flaggedForReview,
            @Param("parentPostId") UUID parentPostId,
            @Param("postType") String postType,
            @Param("hasContentWarning") Boolean hasContentWarning,
            @Param("search") String search,
            @Param("isDeleted") Boolean isDeleted,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
            SELECT COUNT(*) FROM forum_posts
            WHERE (:threadId IS NULL OR thread_id = :threadId::uuid)
                AND (:authorId IS NULL OR author_id = :authorId::uuid)
                AND (:flaggedForReview IS NULL OR flagged_for_review = :flaggedForReview)
                AND (:parentPostId IS NULL OR parent_post_id = :parentPostId::uuid)
                AND (:postType IS NULL OR post_type = :postType::post_type_enum)
                AND (:hasContentWarning IS NULL OR
                    (CASE WHEN :hasContentWarning = true
                          THEN content_warning_type != 'NONE'
                          ELSE content_warning_type = 'NONE' END))
                AND (:search IS NULL OR
                    LOWER(content) LIKE '%' || LOWER(:search) || '%')
                AND is_deleted = :isDeleted
    """)
    Mono<Long> countPostsWithFilters(
            @Param("threadId") UUID threadId,
            @Param("authorId") UUID authorId,
            @Param("flaggedForReview") Boolean flaggedForReview,
            @Param("parentPostId") UUID parentPostId,
            @Param("postType") String postType,
            @Param("hasContentWarning") Boolean hasContentWarning,
            @Param("search") String search,
            @Param("isDeleted") Boolean isDeleted
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
