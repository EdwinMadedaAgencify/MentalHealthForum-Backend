package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkedThreadRecord;
import com.mentalhealthforum.mentalhealthforum_backend.model.ThreadBookmarkEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ThreadBookmarkRepository extends R2dbcRepository<ThreadBookmarkEntity, UUID> {

    Mono<Boolean> existsByUserIdAndThreadId(UUID userId, UUID threadId);

    Mono<Void> deleteByUserIdAndThreadId(UUID userId, UUID threadId);

    Flux<ThreadBookmarkEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
        SELECT b.id as bookmark_id,
               t.id as thread_id,
               t.title,
               t.creator_id,
               t.post_count,
               t.view_count,
               t.last_activity_at,
               b.created_at as bookmarked_at,
               b.notes as bookmark_notes
        FROM forum_threads t
        INNER JOIN thread_bookmarks b ON t.id = b.thread_id
        WHERE b.user_id = :userId
        AND t.is_deleted = false
        AND (:search IS NULL OR (
               LOWER(t.title) LIKE '%' || LOWER(:search) || '%' OR
               LOWER(b.notes) LIKE '%' || LOWER(:search) || '%'
           ))
        ORDER BY
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'title' THEN t.title
                        WHEN 'bookmarked_at' THEN b.created_at::text
                        WHEN 'last_activity_at' THEN t.last_activity_at::text
                        WHEN 'post_count' THEN LPAD(t.post_count::text, 10, '0')
                        ELSE b.created_at::text
                    END
                ELSE NULL
            END DESC NULLS LAST,
    
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'title' THEN t.title
                        WHEN 'bookmarked_at' THEN b.created_at::text
                        WHEN 'last_activity_at' THEN t.last_activity_at::text
                        WHEN 'post_count' THEN LPAD(t.post_count::text, 10, '0')
                        ELSE b.created_at::text
                    END
                ELSE NULL
            END ASC NULLS FIRST
        LIMIT :limit OFFSET :offset
    """)
    Flux<BookmarkedThreadRecord> findBookmarkedThreadsPaginated(
            @Param("userId") UUID userId,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM thread_bookmarks b
        INNER JOIN forum_threads t ON b.thread_id = t.id
        WHERE b.user_id = :userId
        AND t.is_deleted = false
        AND (:search IS NULL OR (
                    LOWER(t.title) LIKE '%' || LOWER(:search) || '%' OR
                    LOWER(b.notes) LIKE '%' || LOWER(:search) || '%'
                ))
    """)
    Mono<Long> countBookmarksWithFilters(
           @Param("userId") UUID userId,
           @Param("search") String search);

    @Query("SELECT COUNT(*) FROM thread_bookmarks WHERE user_id = :userId")
    Mono<Long> countByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(*) FROM thread_bookmarks WHERE user_id = :threadId")
    Mono<Long> countByThreadId(@Param("threadId") UUID threadId);
}
