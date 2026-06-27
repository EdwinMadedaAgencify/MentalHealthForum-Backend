package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.contentReportsComprehensiveSafety.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ReportCategory;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ReportStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ReportTargetType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.Severity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReportService {

    // TODO: Future - Check if user is report-banned
    // if (userIsReportBanned(reporterId)) {
    //     return Mono.error(new ApiException("You are temporarily banned from submitting reports", ErrorCode.FORBIDDEN));
    // }


    Mono<ReportResponse> createThreadReport(CreateThreadReportRequest request, ViewerContext viewerContext);

    Mono<ReportResponse> createPostReport(CreatePostReportRequest request, ViewerContext viewerContext);

    Mono<ReportResponse> createUserReport(CreateUserReportRequest request, ViewerContext viewerContext);

    Mono<PaginatedResponse<ReportResponse>> getOwnReports(
            int page,
            int size,
            ReportTargetType targetType,
            ReportStatus status,
            ReportCategory category,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    );

    Mono<PaginatedResponse<ReportResponse>> getAllReports(
            int page,
            int size,
            UUID reporterId,
            UUID reportedUserId,
            UUID threadId,
            UUID postId,
            ReportTargetType targetType,
            ReportStatus status,
            ReportCategory category,
            Severity severity,
            UUID assignedTo,
            UUID reviewedBy,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext);

    Mono<ReportResponse> getReportById(UUID reportId, ViewerContext viewerContext);

    Mono<ReportResponse> assignReport(UUID reportId, UUID moderatorId, ViewerContext viewerContext);

    Mono<ReportResponse> resolveReport(UUID reportId, ResolveReportRequest request, ViewerContext viewerContext);

    Mono<ReportResponse> dismissReport(UUID reportId, DismissReportRequest reason, ViewerContext viewerContext);

    Mono<ReportResponse> escalateReport(UUID reportId, EscalateReportRequest request, ViewerContext viewerContext);

    Mono<ReportResponse> updateReportDetails(UUID reportId, UpdateReportDetailsRequest request, ViewerContext viewerContext);

    // ==================== USER REPORT HISTORY ====================
    Mono<UserReportHistoryResponse> getOwnReportHistory(ViewerContext viewerContext);

    Mono<UserReportHistoryResponse> getUserReportHistory(UUID userId, ViewerContext viewerContext);

    Flux<ReportTemplateResponse> getReportTemplates();

    Flux<ReportTemplateResponse> getReportTemplatesByCategory(ReportCategory category);

    Flux<ModerationActionTemplateResponse> getModerationActionTemplates(ViewerContext viewerContext);

    Flux<DismissalReasonTemplateResponse> getDismissalReasonTemplates(ViewerContext viewerContext);
}
