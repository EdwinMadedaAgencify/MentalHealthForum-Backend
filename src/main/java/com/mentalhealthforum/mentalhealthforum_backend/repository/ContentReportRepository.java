package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ReportStatus;
import com.mentalhealthforum.mentalhealthforum_backend.model.ContentReportEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ContentReportRepository extends R2dbcRepository<ContentReportEntity, UUID> {

    // TODO: BACKEND SIDE EFFECTS ORCHESTRATION:
// The polymorphic active target matching strategy used below (checking non-null post_id,
// thread_id, or reported_user_id) must be mapped directly to a future downstream
// orchestrator. When a moderator transitions a report's status to 'RESOLVED',
// a separate service must read these target fields to automatically trigger the
// corresponding side effects (e.g., hiding a post or triggering user bans) within
// the same TransactionalOperator pipeline.

    // Basic queries
    Flux<ContentReportEntity> findByStatusOrderByReportedAtDesc(ReportStatus status);

    Flux<ContentReportEntity> findByAssignedModeratorId(UUID moderator);

    Flux<ContentReportEntity> findByReporterId(UUID reporterId);

    // ==================== USER QUERIES (Own Reports) ====================

    @Query("""
        SELECT * FROM content_reports
        WHERE reporter_id = :reporterId::uuid
              AND (:targetType IS NULL OR target_type = :targetType::report_target_type_enum)
              AND (:status IS NULL OR status = :status::report_status_enum)
              AND (:category IS NULL OR report_category = :category::report_category_enum)
              AND (:search IS NULL OR
                    LOWER(reason) LIKE '%' || LOWER(:search) || '%' OR
                    LOWER(details) LIKE '%' || LOWER(:search) || '%')
        ORDER BY
            -- Sort DESC
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'severity' THEN
                            CASE severity
                                WHEN 'CRITICAL' THEN '1'
                                WHEN 'HIGH' THEN '2'
                                WHEN 'MEDIUM' THEN '3'
                                WHEN 'LOW' THEN '4'
                                ELSE '5'
                            END
                        ELSE EXTRACT(EPOCH FROM reported_at)::text
                    END
                ELSE NULL
            END DESC NULLS LAST,
    
            -- Sort ASC
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'severity' THEN
                            CASE severity
                                WHEN 'CRITICAL' THEN '1'
                                WHEN 'HIGH' THEN '2'
                                WHEN 'MEDIUM' THEN '3'
                                WHEN 'LOW' THEN '4'
                                ELSE '5'
                            END
                        ELSE EXTRACT(EPOCH FROM reported_at)::text
                    END
                ELSE NULL
            END ASC NULLS FIRST,
    
            -- Tie breaker for deterministic ordering
            id
        LIMIT :limit OFFSET :offset
    """)
    Flux<ContentReportEntity> findOwnReportsPaginated(
            @Param("reporterId") UUID reporterId,
            @Param("targetType") String targetType,
            @Param("status") String status,
            @Param("category") String category,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
         SELECT COUNT(*) FROM content_reports
         WHERE reporter_id = :reporterId::uuid
              AND (:targetType IS NULL OR target_type = :targetType::report_target_type_enum)
              AND (:status IS NULL OR status = :status::report_status_enum)
              AND (:category IS NULL OR report_category = :category::report_category_enum)
              AND (:search IS NULL OR
                    LOWER(reason) LIKE '%' || LOWER(:search) || '%' OR
                    LOWER(details) LIKE '%' || LOWER(:search) || '%')
     """)
    Mono<Long> countOwnReportsWithFilters(
            @Param("reporterId") UUID reporterId,
            @Param("targetType") String targetType,
            @Param("status") String status,
            @Param("category") String category,
            @Param("search") String search
    );

    // ==================== MODERATOR QUERIES (All Reports) ====================

    @Query("""
        SELECT * FROM content_reports
        WHERE (:reporterId IS NULL OR reporter_id = :reporterId::uuid)
          AND (:reportedUserId IS NULL OR reported_user_id = :reportedUserId::uuid)
          AND (:threadId IS NULL OR thread_id = :threadId::uuid)
          AND (:postId IS NULL OR post_id = :postId::uuid)
          AND (:targetType IS NULL OR target_type = :targetType::report_target_type_enum)
          AND (:status IS NULL OR status = :status::report_status_enum)
          AND (:category IS NULL OR report_category = :category::report_category_enum)
          AND (:severity IS NULL OR severity = :severity::severity_enum)
          AND (:assignedTo IS NULL OR assigned_moderator_id = :assignedTo::uuid)
          AND (:reviewedBy IS NULL OR reviewed_by = :reviewedBy::uuid)
          AND (:search IS NULL OR
                LOWER(reason) LIKE '%' || LOWER(:search) || '%' OR
                LOWER(details) LIKE '%' || LOWER(:search) || '%')
        ORDER BY
            -- Sort DESC
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'severity' THEN
                            CASE severity
                                WHEN 'CRITICAL' THEN '1'
                                WHEN 'HIGH' THEN '2'
                                WHEN 'MEDIUM' THEN '3'
                                WHEN 'LOW' THEN '4'
                                ELSE '5'
                            END
                        ELSE EXTRACT(EPOCH FROM reported_at)::text
                    END
                ELSE NULL
            END DESC NULLS LAST,
    
            -- Sort ASC
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'severity' THEN
                            CASE severity
                                WHEN 'CRITICAL' THEN '1'
                                WHEN 'HIGH' THEN '2'
                                WHEN 'MEDIUM' THEN '3'
                                WHEN 'LOW' THEN '4'
                                ELSE '5'
                            END
                        ELSE EXTRACT(EPOCH FROM reported_at)::text
                    END
                ELSE NULL
            END ASC NULLS FIRST,
    
            -- Tie breaker for deterministic ordering
            id
        LIMIT :limit OFFSET :offset
    """)
    Flux<ContentReportEntity> findAllReportsPaginated(
            @Param("reporterId") UUID reporterId,
            @Param("reportedUserId") UUID reportedUserId,
            @Param("threadId") UUID threadId,
            @Param("postId")  UUID postId,
            @Param("targetType") String targetType,
            @Param("status") String status,
            @Param("category") String category,
            @Param("severity") String severity,
            @Param("assignedTo") UUID assignedTo,
            @Param("reviewedBy") UUID reviewedBy,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM content_reports
        WHERE (:reporterId IS NULL OR reporter_id = :reporterId::uuid)
          AND (:reportedUserId IS NULL OR reported_user_id = :reportedUserId::uuid)
          AND (:threadId IS NULL OR thread_id = :threadId::uuid)
          AND (:postId IS NULL OR post_id = :postId::uuid)
          AND (:targetType IS NULL OR target_type = :targetType::report_target_type_enum)
          AND (:status IS NULL OR status = :status::report_status_enum)
          AND (:category IS NULL OR report_category = :category::report_category_enum)
          AND (:severity IS NULL OR severity = :severity::severity_enum)
          AND (:assignedTo IS NULL OR assigned_moderator_id = :assignedTo::uuid)
          AND (:reviewedBy IS NULL OR reviewed_by = :reviewedBy::uuid)
          AND (:search IS NULL OR
                LOWER(reason) LIKE '%' || LOWER(:search) || '%' OR
                LOWER(details) LIKE '%' || LOWER(:search) || '%')
     """)
    Mono<Long> countAllReportsWithFilters(
            @Param("reporterId") UUID reporterId,
            @Param("reportedUserId") UUID reportedUserId,
            @Param("threadId") UUID threadId,
            @Param("postId")  UUID postId,
            @Param("targetType") String targetType,
            @Param("status") String status,
            @Param("category") String category,
            @Param("severity") String severity,
            @Param("assignedTo") UUID assignedTo,
            @Param("reviewedBy") UUID reviewedBy,
            @Param("search") String search
    );

    // Reports by target
    Flux<ContentReportEntity> findByPostId(UUID postId);

    Flux<ContentReportEntity> findByThreadId(UUID threadId);

    Flux<ContentReportEntity> findByReportedUserId(UUID userId);

    // Check if user already reported this target
    @Query("""    
            SELECT * FROM content_reports
            WHERE reporter_id = :reporterId
            AND status IN ('PENDING', 'UNDER_REVIEW')
            AND (
                (:postId IS NOT NULL AND post_id = :postId) OR
                (:threadId IS NOT NULL AND thread_id = :threadId) OR
                (:reportedUserId IS NOT NULL AND reported_user_id = :reportedUserId)
            )
            LIMIT 1
    """)
    Mono<ContentReportEntity> findActiveReportForTarget(
            @Param("reporterId") UUID reporterId,
            @Param("postId") UUID postId,
            @Param("threadId") UUID threadId,
            @Param("reportedUserId") UUID reportedUserId
    );


}
