package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.ForumThreadEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ForumThreadRepository extends R2dbcRepository<ForumThreadEntity, UUID> {
    // ==================== BASIC QUERIES ====================

    Flux<ForumThreadEntity> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    Mono<ForumThreadEntity> findByIdAndIsDeletedFalse(UUID threadId);

    // ==================== REFERENCE TABLES ====================
    @Query("""
        SELECT * FROM forum_threads
        WHERE (:categoryId IS NULL OR category_id = :categoryId::uuid)
            AND (:creatorId IS NULL OR creator_id = :creatorId::uuid)
            AND (:threadType IS NULL OR thread_type = :threadType::thread_type_enum)
            AND (:threadStatus IS NULL OR thread_status = :threadStatus::thread_status_enum)
            AND (:isDeleted IS NULL OR is_deleted = :isDeleted)
            AND (:isFeatured IS NULL OR is_featured = :isFeatured)
            AND (:hasContentWarning IS NULL OR
                    (CASE WHEN :hasContentWarning = true
                     THEN content_warning_type != 'NONE'
                     ELSE content_warning_type = 'NONE' END))
            AND (:search IS NULL OR
                LOWER(title) LIKE '%' || LOWER(:search) || '%')
        ORDER BY
            is_sticky DESC,
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN created_at::text
                        WHEN 'last_activity_at' THEN last_activity_at::text
                        WHEN 'post_count' THEN LPAD(post_count::text, 10, '0')
                        WHEN 'view_count' THEN LPAD(view_count::text, 10, '0')
                        WHEN 'title' THEN title
                        ELSE last_activity_at::text
                    END
                ELSE NULL
            END DESC NULLS LAST,
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN created_at::text
                        WHEN 'last_activity_at' THEN last_activity_at::text
                        WHEN 'post_count' THEN LPAD(post_count::text, 10, '0')
                        WHEN 'view_count' THEN LPAD(view_count::text, 10, '0')
                        WHEN 'title' THEN title
                        ELSE last_activity_at::text
                    END
                ELSE NULL
            END ASC NULLS FIRST,
            id
        LIMIT :limit OFFSET :offset
        """)
    Flux<ForumThreadEntity> findAllPaginated(
            @Param("categoryId") UUID categoryId,
            @Param("creatorId") UUID creatorId,
            @Param("threadType") String threadType,
            @Param("threadStatus") String threadStatus,
            @Param("isDeleted") Boolean isDeleted,
            @Param("isFeatured") Boolean isFeatured,
            @Param("hasContentWarning") Boolean hasContentWarning,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM forum_threads
        WHERE (:categoryId IS NULL OR category_id = :categoryId::uuid)
            AND (:creatorId IS NULL OR creator_id = :creatorId::uuid)
            AND (:threadType IS NULL OR thread_type = :threadType::thread_type_enum)
            AND (:threadStatus IS NULL OR thread_status = :threadStatus::thread_status_enum)
            AND (:isDeleted IS NULL OR is_deleted = :isDeleted)
            AND (:isFeatured IS NULL OR is_featured = :isFeatured)
            AND (:hasContentWarning IS NULL OR
                    (CASE WHEN :hasContentWarning = true
                     THEN content_warning_type != 'NONE'
                     ELSE content_warning_type = 'NONE' END))
            AND (:search IS NULL OR
                LOWER(title) LIKE '%' || LOWER(:search) || '%')
        """)
    Mono<Long> countAllPaginated(
            @Param("categoryId") UUID categoryId,
            @Param("creatorId") UUID creatorId,
            @Param("threadType") String threadType,
            @Param("threadStatus") String threadStatus,
            @Param("isDeleted") Boolean isDeleted,
            @Param("isFeatured") Boolean isFeatured,
            @Param("hasContentWarning") Boolean hasContentWarning,
            @Param("search") String search
    );

    // ==================== CATEGORY-SPECIFIC PAGINATED QUERIES ====================
    @Query("""
        SELECT * FROM forum_threads
        WHERE category_id = :categoryId
            AND is_deleted = false
        ORDER BY is_sticky DESC, last_activity_at DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<ForumThreadEntity> findActiveThreadsByCategoryPaginated(
            @Param("categoryId") UUID categoryId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM forum_threads
        WHERE category_id = :categoryId
            AND is_deleted = false
        """)
    Mono<Long> countActiveThreadsByCategory(
            @Param("categoryId") UUID categoryId
    );

    // ==================== VIEW COUNT ====================


    @Query("UPDATE forum_threads SET view_count = view_count + 1 WHERE id = :threadId")
    Mono<Void> incrementViewCount(@Param("threadId") UUID threadId);

    // ==================== SOFT DELETE ====================
    @Query("UPDATE forum_threads SET is_deleted = true, updated_at = NOW() WHERE id = :threadId")
    Mono<Void> softDeleteThread(@Param("threadId") UUID threadId);

    @Query("UPDATE forum_threads SET is_deleted = false, updated_at = NOW() WHERE id = :threadId")
    Mono<Void> restoreThread(@Param("threadId") UUID threadId);

    // ==================== BEST ANSWER ====================
    @Query("""
        UPDATE forum_threads
        SET best_answer_post_id = :postId,
            resolved_at = NOW(),
            resolved_by_user_id = :resolvedByUserId,
            thread_status = 'RESOLVED',
            updated_at = NOW()
        WHERE id = :threadId
        """)
    Mono<Void> setBestAnswer(
            @Param("postId") UUID postId,
            @Param("threadId") UUID threadId,
            @Param("resolvedByUserId") UUID resolvedByUserId
    );

    @Query("""
        UPDATE forum_threads
        SET best_answer_post_id = NULL,
            resolved_at = NULL,
            thread_status = 'OPEN',
            updated_at = NOW()
        WHERE id = :threadId
        """)
    Mono<Void> clearBestAnswer(
            @Param("threadId") UUID threadId
    );



    // ==================== EXISTENCE CHECKS ====================
    Mono<Boolean> existsByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT EXISTS(SELECT 1 FROM forum_threads WHERE category_id = :categoryId)")
    Mono<Boolean> existsByCategoryId(@Param("categoryId") UUID categoryId);

    // ==================== THREAD ACTIONS ====================

    @Query("UPDATE forum_threads SET thread_status = :status::thread_status_enum, updated_at = NOW() WHERE id = :threadId")
    Mono<Void> updateThreadStatus(@Param("threadId") UUID threadId,@Param("status") String status);

    @Query("UPDATE forum_threads SET lock_reason = NULL, locked_by = NULL, locked_at = NULL WHERE id = :threadId")
    Mono<Void> clearLockMetadata(@Param("threadId") UUID threadId);

    @Query("UPDATE forum_threads SET lock_reason = :lockReason, locked_by = :moderatorId, locked_at = NOW() WHERE id = :threadId")
    Mono<Void> updateLockReason(@Param("threadId") UUID threadId,@Param("lockReason") String lockReason, @Param("moderatorId") UUID moderatorId);

    @Query("UPDATE forum_threads SET is_sticky = :sticky, updated_at = NOW() WHERE id = :threadId")
    Mono<Void> updateStickyStatus(@Param("threadId") UUID threadId,@Param("sticky") boolean sticky);

    @Query("UPDATE forum_threads SET is_featured = :featured, updated_at = NOW() WHERE id = :threadId")
    Mono<Void> updateFeaturedStatus(@Param("threadId") UUID threadId, @Param("featured") boolean featured);

    @Query("UPDATE forum_threads SET category_id = :categoryId, updated_at = NOW() WHERE id = :threadId")
    Mono<Void> moveThread(@Param("threadId") UUID threadId,@Param("categoryId") UUID categoryId);

    // ==================== POST COUNT OPERATIONS ====================

    // For batch operations (merge, split)
    @Query("UPDATE forum_threads SET post_count = post_count + :increment WHERE id = :threadId")
    Mono<Void> incrementPostCount(@Param("threadId") UUID threadId, @Param("increment") int increment);


    @Query("UPDATE forum_threads SET post_count = post_count - :decrement WHERE id = :threadId")
    Mono<Void> decrementPostCount(@Param("threadId") UUID threadId, @Param("decrement") int decrement);

    // Recalculate from scratch (when counts get out of sync)
    @Query("UPDATE forum_threads SET post_count = (SELECT COUNT(*) FROM forum_posts WHERE thread_id = :threadId AND is_deleted = false) WHERE id = :threadId")
    Mono<Void> recalculatePostCount(@Param("threadId") UUID id);

    @Query("UPDATE forum_threads SET last_activity_at = NOW() WHERE id = :threadId")
    Mono<Void> updateLastActivity(@Param("threadId") UUID threadId);


}
