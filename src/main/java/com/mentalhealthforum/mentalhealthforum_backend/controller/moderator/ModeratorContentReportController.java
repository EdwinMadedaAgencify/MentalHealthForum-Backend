package com.mentalhealthforum.mentalhealthforum_backend.controller.moderator;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.contentReportsComprehensiveSafety.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ReportCategory;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ReportStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ReportTargetType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.Severity;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import com.mentalhealthforum.mentalhealthforum_backend.service.ReportService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/moderator/forum/content-reports")
public class ModeratorContentReportController {

    private final ReportService reportService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public ModeratorContentReportController(
            ReportService reportService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.reportService = reportService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    // ==================== REPORT MANAGEMENT ====================

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<ReportResponse>>>> getAllReports(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "reporter_id") UUID reporterId,
            @RequestParam(required = false, name = "reported_user_id") UUID reportedUserId,
            @RequestParam(required = false, name = "thread_id") UUID threadId,
            @RequestParam(required = false, name = "post_id") UUID postId,
            @RequestParam(required = false, name = "target_type") ReportTargetType targetType,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportCategory category,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false, name = "assigned_to") UUID assignedTo,
            @RequestParam(required = false, name = "reviewed_by") UUID reviewedBy,
            @RequestParam(defaultValue = "", name = "search") @Parameter(description = "Search by reason or details") String search,
            @RequestParam(defaultValue = "reported_at", name = "sort_by") @Parameter(description = "Sort by: severity, last_modified_at, reported_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction") @Parameter(description = "Sort direction: asc or desc") String sortDirection
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return reportService.getAllReports(page, size, reporterId, reportedUserId, threadId, postId, targetType, status, category, severity, assignedTo, reviewedBy, search, sortBy, sortDirection, viewerContext)
                .map(reports->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Content reports retrieved successfully", reports)));
    }

    @GetMapping("/{reportId}")
    public Mono<ResponseEntity<StandardSuccessResponse<ReportResponse>>> getReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId
            ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return reportService.getReportById(reportId, viewerContext)
                .map(report->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Content report retrieved successfully", report)));
    }

    @PatchMapping("/{reportId}/assign")
    public Mono<ResponseEntity<StandardSuccessResponse<ReportResponse>>> assignReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @RequestParam UUID moderatorId
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return reportService.assignReport(reportId, moderatorId, viewerContext)
                .map(report->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Content report assigned successfully", report)));
    }

    @PatchMapping("/{reportId}/resolve")
    public Mono<ResponseEntity<StandardSuccessResponse<ReportResponse>>> resolveReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @Valid @RequestBody ResolveReportRequest request
            ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return reportService.resolveReport(reportId, request, viewerContext)
                .map(report->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Content report resolved successfully", report)));
    }

    @PatchMapping("/{reportId}/dismiss")
    public Mono<ResponseEntity<StandardSuccessResponse<ReportResponse>>> dismissReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @Valid @RequestBody DismissReportRequest request
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return reportService.dismissReport(reportId, request, viewerContext)
                .map(report->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Content report dismissed successfully", report)));
    }

    @PatchMapping("/{reportId}/esclate")
    public Mono<ResponseEntity<StandardSuccessResponse<ReportResponse>>> escalateReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @Valid @RequestBody EscalateReportRequest request
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return reportService.escalateReport(reportId, request, viewerContext)
                .map(report->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Content report escalated successfully", report)));
    }

    @PatchMapping("/{reportId}/details")
    public Mono<ResponseEntity<StandardSuccessResponse<ReportResponse>>> updatedReportDetails(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @Valid @RequestBody UpdateReportDetailsRequest request
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return reportService.updateReportDetails(reportId, request, viewerContext)
                .map(report->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Content report details updated successfully", report)));
    }

    // ==================== USER REPORT HISTORY (Moderator View) ====================

    @GetMapping("/users/{userId}/history")
    public Mono<ResponseEntity<StandardSuccessResponse<UserReportHistoryResponse>>> getUserReportHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return reportService.getUserReportHistory(userId, viewerContext)
                .map(history ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Content user report history retrieved successfully", history)));
    }

    // ==================== REFERENCE DATA ====================

    @GetMapping("/templates/actions")
    public Mono<ResponseEntity<StandardSuccessResponse<List<ModerationActionTemplateResponse>>>> getModeratorActionTemplates(
            @AuthenticationPrincipal Jwt jwt
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        return reportService.getModerationActionTemplates(viewerContext)
                .collectList()
                .map(templates ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Moderation action templates retrieved successfully", templates)));
    }

    @GetMapping("/templates/dismissal-reason")
    public Mono<ResponseEntity<StandardSuccessResponse<List<DismissalReasonTemplateResponse>>>> getDismissalReasonTemplates(
            @AuthenticationPrincipal Jwt jwt
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        return reportService.getDismissalReasonTemplates(viewerContext)
                .collectList()
                .map(templates ->
                        ResponseEntity.ok(new StandardSuccessResponse<>("Dismissal reason templates retrieved successfully", templates)));
    }

}
