package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.WatchStatusRecord;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.WatchThreadRecord;
import com.mentalhealthforum.mentalhealthforum_backend.model.WatchThreadEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Repository
public interface WatchThreadRepository extends R2dbcRepository<WatchThreadEntity, UUID> {

    Mono<Boolean> existsByUserIdAndThreadId(UUID userId, UUID threadId);

    Mono<Void> deleteByUserIdAndThreadId(UUID userId, UUID threadId);

    Mono<Long> countByUserId(UUID userId);

    /**
     * Batch fetch watch status for threads
     */
    @Query("""
        SELECT w.thread_id as thread_id, true as is_watched
        FROM watch_threads w
        WHERE w.thread_id IN (:threadIds) AND w.user_id = :userId
    """)
    Flux<WatchStatusRecord> findWatchStatusForThreads(
            @Param("userId") UUID userId,
            @Param("threadIds")List<UUID> threadIds
    );


    @Query("""
        SELECT wt.id as watch_id,
               wt.notification_enabled,
               wt.created_at as watched_at,
               t.id as thread_id,
               t.title as thread_title,
               t.creator_id,
               t.category_id,
               t.thread_type,
               t.thread_status,
               t.content_warning_type,
               t.post_count,
               t.view_count,
               t.last_activity_at,
               t.is_sticky,
               t.is_featured
        FROM watch_threads wt
        INNER JOIN forum_threads t ON wt.thread_id = t.id
        WHERE wt.id = :watchId AND wt.user_id = :userId
    """)
    Mono<WatchThreadRecord> findWatchById(
        @Param("watchId") UUID watchId,
        @Param("userId") UUID userId
    );

    @Query("""
        SELECT wt.id as watch_id,
               wt.notification_enabled,
               wt.created_at as watched_at,
               t.id as thread_id,
               t.title as thread_title,
               t.creator_id,
               t.category_id,
               t.thread_type,
               t.thread_status,
               t.content_warning_type,
               t.post_count,
               t.view_count,
               t.last_activity_at,
               t.is_sticky,
               t.is_featured
        FROM watch_threads wt
        INNER JOIN forum_threads t ON wt.thread_id = t.id
        INNER JOIN forum_categories c ON t.category_id = c.id
        WHERE wt.user_id = :viewerId
            AND t.is_deleted = false

            --Search: Hybrid: FTS + Trigram
            AND (:search IS NULL
                    OR to_tsvector('public.english_unaccent', coalesce(t.title, ''))
                        @@ websearch_to_tsquery('public.english_unaccent', :search)
                    OR public.unaccent_immutable(t.title) % public.unaccent_immutable(:search)
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
    
            AND (:notificationEnabled IS NULL OR wt.notification_enabled = :notificationEnabled)
            AND (:categoryId IS NULL OR t.category_id = :categoryId)
            AND (:creatorId IS NULL OR t.creator_id = :creatorId)
            AND (:threadType IS NULL OR t.thread_type = :threadType::thread_type_enum)
            AND (:threadStatus IS NULL OR t.thread_status = :threadStatus::thread_status_enum)
            AND (:hasContentWarning IS NULL OR
                (CASE WHEN :hasContentWarning = true
                 THEN t.content_warning_type != 'NONE'
                 ELSE t.content_warning_type = 'NONE' END))
            AND (:isBookmarked IS NULL OR
                (:isBookmarked = true AND EXISTS (
                    SELECT 1 FROM thread_bookmarks b
                    WHERE b.thread_id = t.id AND b.user_id = :viewerId
                    )) OR
                (:isBookmarked = false AND NOT EXISTS (
                    SELECT 1 FROM thread_bookmarks b
                    WHERE b.thread_id = t.id AND b.user_id = :viewerId
                    ))
                )
 
        ORDER BY
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN wt.created_at::text
                        WHEN 'last_activity_at' THEN t.last_activity_at::text
                        WHEN 'thread_title' THEN t.title
                        WHEN 'post_count' THEN LPAD(t.post_count::text, 10, '0')
                        WHEN 'view_count' THEN LPAD(t.view_count::text, 10, '0')
                        ELSE wt.created_at::text
                    END
                ELSE NULL
            END DESC NULLS LAST,
    
    
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'created_at' THEN wt.created_at::text
                        WHEN 'last_activity_at' THEN t.last_activity_at::text
                        WHEN 'thread_title' THEN t.title
                        WHEN 'post_count' THEN LPAD(t.post_count::text, 10, '0')
                        WHEN 'view_count' THEN LPAD(t.view_count::text, 10, '0')
                        ELSE wt.created_at::text
                    END
                ELSE NULL
            END ASC NULLS FIRST
        LIMIT :limit OFFSET :offset
    """)
    Flux<WatchThreadRecord> findPaginatedByUserId(
            @Param("viewerId") UUID viewerId,
            @Param("isAdmin") boolean isAdmin,
            @Param("isModeratorOrAdmin") boolean isModeratorOrAdmin,
            @Param("isVerified") boolean isVerified,
            @Param("categoryId") UUID categoryId,
            @Param("creatorId") UUID creatorId,
            @Param("threadType") String threadType,
            @Param("threadStatus") String threadStatus,
            @Param("hasContentWarning") Boolean hasContentWarning,
            @Param("isBookmarked") Boolean isBookmarked,
            @Param("notificationEnabled") Boolean notificationEnabled,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM watch_threads wt
        INNER JOIN forum_threads t ON wt.thread_id = t.id
        INNER JOIN forum_categories c ON t.category_id = c.id
        WHERE wt.user_id = :viewerId
            AND t.is_deleted = false
    
            AND (:search IS NULL
                    OR to_tsvector('public.english_unaccent', coalesce(t.title, ''))
                        @@ websearch_to_tsquery('public.english_unaccent', :search)
                    OR public.unaccent_immutable(t.title) % public.unaccent_immutable(:search)
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
    
            AND (:notificationEnabled IS NULL OR wt.notification_enabled = :notificationEnabled)
            AND (:categoryId IS NULL OR t.category_id = :categoryId)
            AND (:creatorId IS NULL OR t.creator_id = :creatorId)
            AND (:threadType IS NULL OR t.thread_type = :threadType::thread_type_enum)
            AND (:threadStatus IS NULL OR t.thread_status = :threadStatus::thread_status_enum)
            AND (:hasContentWarning IS NULL OR
                (CASE WHEN :hasContentWarning = true
                 THEN t.content_warning_type != 'NONE'
                 ELSE t.content_warning_type = 'NONE' END))
            AND (:isBookmarked IS NULL OR
                (:isBookmarked = true AND EXISTS (
                    SELECT 1 FROM thread_bookmarks b
                    WHERE b.thread_id = t.id AND b.user_id = :viewerId
                    )) OR
                (:isBookmarked = false AND NOT EXISTS (
                    SELECT 1 FROM thread_bookmarks b
                    WHERE b.thread_id = t.id AND b.user_id = :viewerId
                    ))
                )
    """)
    Mono<Long> countByUserIdWithFilters(
            @Param("viewerId") UUID viewerId,
            @Param("isAdmin") boolean isAdmin,
            @Param("isModeratorOrAdmin") boolean isModeratorOrAdmin,
            @Param("isVerified") boolean isVerified,
            @Param("categoryId") UUID categoryId,
            @Param("creatorId") UUID creatorId,
            @Param("threadType") String threadType,
            @Param("threadStatus") String threadStatus,
            @Param("hasContentWarning") Boolean hasContentWarning,
            @Param("isBookmarked") Boolean isBookmarked,
            @Param("notificationEnabled") Boolean notificationEnabled,
            @Param("search") String search
    );

}
