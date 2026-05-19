package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.contentReportsComprehensiveSafety.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.*;
import com.mentalhealthforum.mentalhealthforum_backend.repository.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;


@Service
public class ReportServiceImpl implements ReportService {

    public static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final ContentReportRepository contentReportRepository;
    private final ReportTemplateRepository reportTemplateRepository;
    private final ModerationActionTemplateRepository moderationActionTemplateRepository;
    private final DismissalReasonTemplateRepository dismissalReasonTemplateRepository;
    private final UserReportHistoryRepository userReportHistoryRepository;
    private final PostRepository postRepository;
    private final ForumThreadRepository forumThreadRepository;
    private final AppUserRepository appUserRepository;

    public ReportServiceImpl(
            TransactionalOperator transactionalOperator,
            ContentReportRepository contentReportRepository,
            ReportTemplateRepository reportTemplateRepository,
            ModerationActionTemplateRepository moderationActionTemplateRepository,
            DismissalReasonTemplateRepository dismissalReasonTemplateRepository,
            UserReportHistoryRepository userReportHistoryRepository,
            PostRepository postRepository,
            ForumThreadRepository forumThreadRepository,
            AppUserRepository appUserRepository) {
        this.transactionalOperator = transactionalOperator;
        this.contentReportRepository = contentReportRepository;
        this.reportTemplateRepository = reportTemplateRepository;
        this.moderationActionTemplateRepository = moderationActionTemplateRepository;
        this.dismissalReasonTemplateRepository = dismissalReasonTemplateRepository;
        this.userReportHistoryRepository = userReportHistoryRepository;
        this.postRepository = postRepository;
        this.forumThreadRepository = forumThreadRepository;
        this.appUserRepository = appUserRepository;
    }

    // ==================== USER ACTIONS ====================

    @Override
    public Mono<ReportResponse> createReport(CreateReportRequest request, ViewerContext viewerContext) {

        UUID reporterId = UUID.fromString(viewerContext.getUserId());

        // TODO: Future - Check if user is report-banned
        // if (userIsReportBanned(reporterId)) {
        //     return Mono.error(new ApiException("You are temporarily banned from submitting reports", ErrorCode.FORBIDDEN));
        // }


        // Validate target exists
        return validateReportTarget(request)
                .then(validateNotDuplicateReport(reporterId, request))
                .then(createReportEntity(reporterId, request))
                .flatMap(this::mapToResponse)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<PaginatedResponse<ReportResponse>> getOwnReports(
            int page,
            int size,
            ReportTargetType targetType,
            ReportStatus status,
            ReportCategory category,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ) {

        UUID userId = UUID.fromString(viewerContext.getUserId());

       return getReportsWithFilters(page, size, userId, null, null, null, targetType,
               status, category, null, null, search, sortBy, sortDirection);
    }

    // ==================== MODERATOR ACTIONS ====================

    @Override
    public Mono<PaginatedResponse<ReportResponse>> getAllReports(
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
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext) {

        if(!viewerContext.isModeratorOrAdmin()){
            return Mono.error(new ApiException("Only moderators can view all reports", ErrorCode.FORBIDDEN));
        }

        return getReportsWithFilters(page, size, reporterId, reportedUserId, threadId, postId, targetType,
                status, category, severity, assignedTo, search, sortBy, sortDirection);

    }

    @Override
    public Mono<ReportResponse> getReportById(UUID reportId, ViewerContext viewerContext) {

        UUID userId = UUID.fromString(viewerContext.getUserId());

        return findReport(reportId)
                .flatMap(report -> {
                  if(!report.getReporterId().equals(userId) && !viewerContext.isModeratorOrAdmin()){
                        return Mono.error(new ApiException("You can only view your own reports", ErrorCode.FORBIDDEN));
                    }
                    return mapToResponse(report);
                });
    }

    @Override
    public Mono<ReportResponse> assignReport(UUID reportId, UUID moderatorId, ViewerContext viewerContext) {

        UUID requestingUserId = UUID.fromString(viewerContext.getUserId());
        boolean isAdmin = viewerContext.isAdmin();
        // Self-assignment check
        boolean isSelfAssignment = requestingUserId.equals(moderatorId);

        // Only allow if: (self-assignment and moderator) OR (admin assigning anyone)
        if(!isAdmin && !isSelfAssignment){
            return Mono.error(new ApiException("Moderators can only assign reports to themselves", ErrorCode.FORBIDDEN));
        }

        return appUserRepository.findAppUserByKeycloakId(moderatorId.toString())
                .switchIfEmpty(Mono.error(new ApiException("Moderator not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(moderator -> {

                    if(!moderator.isModeratorOrAdmin()){
                        return Mono.error(new ApiException("User is not a moderator or admin", ErrorCode.VALIDATION_FAILED));
                    }

                    return performModeratorAction(reportId, viewerContext,  "assign report",
                            report -> {
                                report.setStatus(ReportStatus.UNDER_REVIEW);
                                report.setAssignedModeratorId(moderatorId);
                                report.setAssignedAt(Instant.now());
                                report.setLastModifiedAt(Instant.now());

                                return contentReportRepository.save(report).
                                        flatMap(this::mapToResponse);
                            },
                            List.of(
                                    new ValidationRule(
                                            report -> !report.isPending() && report.isAssigned(),
                                            "Only pending and unassigned reports can be assigned",
                                            ErrorCode.VALIDATION_FAILED // State issue
                                    )
                            )
                    );
                });
    }

    @Override
    public Mono<ReportResponse> resolveReport(UUID reportId, ResolveReportRequest request, ViewerContext viewerContext) {
        UUID reviewerId = UUID.fromString(viewerContext.getUserId());
        boolean isAdmin = viewerContext.isAdmin();

        return  request.getActionTaken().checkPermission(viewerContext)
                        .then(performModeratorAction(reportId, viewerContext, "resolve report",
                            report -> {

                                // Perform escalation
                                report.setStatus(ReportStatus.ACTION_TAKEN);
                                report.setActionTaken(request.getActionTaken());
                                report.setActionTakenDetails(request.getActionTakenDetails());
                                report.setDismissalReason(null);
                                report.setResolutionNotes(request.getResolutionNotes());
                                report.setReviewedAt(Instant.now());
                                report.setReviewedBy(reviewerId);
                                report.setLastModifiedAt(Instant.now());
                                // dismissalReason remains null for resolve actions
                                return contentReportRepository.save(report)
                                        .flatMap(this::mapToResponse);

                            },
                            List.of(
                                    new ValidationRule(
                                            report -> !report.isUnderReview() && !report.isEscalated(),
                                            "Report must be in UNDER_REVIEW or ESCALATED status to resolve",
                                            ErrorCode.VALIDATION_FAILED // State issue
                                    ),
                                    new ValidationRule(
                                            report -> report.isUnderReview() && !isAdmin && !report.isAssignedTo(reviewerId),
                                            "You can only resolve reports assigned to you",
                                            ErrorCode.FORBIDDEN // Permission issue
                                    ),
                                    new ValidationRule(
                                            report -> report.isEscalated() && !isAdmin,
                                            "Only admins can resolve escalated reports",
                                            ErrorCode.VALIDATION_FAILED // Permission issue
                                    )
                            ))
                        );
    }

    @Override
    public Mono<ReportResponse> dismissReport(UUID reportId, DismissReportRequest request, ViewerContext viewerContext) {

        UUID reviewerId = UUID.fromString(viewerContext.getUserId());
        boolean isAdmin = viewerContext.isAdmin();

        return ModerationAction.REPORT_DISMISSED.checkPermission(viewerContext)
                .then(performModeratorAction(reportId, viewerContext, "dismiss report",
                report -> {

                    report.setStatus(ReportStatus.DISMISSED);
                    report.setActionTaken(ModerationAction.REPORT_DISMISSED);
                    report.setActionTakenDetails(request.details());
                    report.setDismissalReason(request.reasonCode());
                    report.setResolutionNotes(request.resolutionNotes() );
                    report.setReviewedAt(Instant.now());
                    report.setReviewedBy(reviewerId);
                    report.setLastModifiedAt(Instant.now());

                    return contentReportRepository.save(report)
                            .flatMap(this::mapToResponse);
                },
                List.of(
                        new ValidationRule(
                                report -> !report.isUnderReview() && !report.isEscalated(),
                                "Report must be in UNDER_REVIEW or ESCALATED status to dismiss",
                                ErrorCode.VALIDATION_FAILED
                        ),
                        new ValidationRule(
                                report -> report.isUnderReview() && !isAdmin && !report.isAssignedTo(reviewerId),
                                "You can only dismiss reports assigned to you",
                                ErrorCode.FORBIDDEN
                        ),
                        new ValidationRule(
                                report -> report.isEscalated() && !isAdmin,
                                "Only admins can dismiss escalated reports",
                                ErrorCode.FORBIDDEN
                        )
                ))
        );
    }

    @Override
    public Mono<ReportResponse> escalateReport(UUID reportId, EscalateReportRequest request, ViewerContext viewerContext) {

        UUID moderatorId = UUID.fromString(viewerContext.getUserId());
        boolean isAdmin = viewerContext.isAdmin();

        return ModerationAction.REPORT_ESCALATED.checkPermission(viewerContext)
                .then(performModeratorAction(reportId, viewerContext, "escalate reports",
                    report -> {

                    report.setStatus(ReportStatus.ESCALATED);
                    report.setActionTaken(ModerationAction.REPORT_ESCALATED);
                    report.setActionTakenDetails(request.reason());
                    report.setResolutionNotes(request.resolutionNotes());
                    report.setLastModifiedAt(Instant.now());
                    // Keep existing assignedModeratorId for audit trail

                    return contentReportRepository.save(report)
                            .flatMap(this::mapToResponse);
                },
                List.of(
                        new ValidationRule(
                                report -> !report.isUnderReview(),
                                "Only reports under review can be escalated",
                                ErrorCode.VALIDATION_FAILED
                        ),
                        new ValidationRule(
                                report -> !isAdmin && !report.isAssignedTo(moderatorId),
                                "You can only escalate reports assigned to you",
                                ErrorCode.FORBIDDEN
                        ),
                        new ValidationRule(
                                ContentReportEntity::isEscalated,
                                "Report is already escalated",
                                ErrorCode.VALIDATION_FAILED
                        )
                ))
        );
    }


    @Override
    public Mono<ReportResponse> updateReportDetails(UUID reportId, UpdateReportDetailsRequest request, ViewerContext viewerContext) {

        UUID moderatorId = UUID.fromString(viewerContext.getUserId());
        boolean isAdmin = viewerContext.isAdmin();

        return ModerationAction.REPORT_DETAILS_UPDATED.checkPermission(viewerContext)
                .then(performModeratorAction(reportId, viewerContext, "update report details",
                report -> {

                    boolean updated = false;

                    if(request.getSeverity() != null && request.getSeverity() != report.getSeverity()){
                        report.setSeverity(request.getSeverity());
                        updated = true;
                    }

                    if(request.getResolutionNotes() != null && !request.getResolutionNotes().equals(report.getResolutionNotes())){
                        report.setResolutionNotes(request.getResolutionNotes());
                        updated = true;
                    }

                    if(request.getActionTaken() != null && !request.getActionTaken().equals(report.getActionTaken())){
                        report.setActionTaken(request.getActionTaken());
                        updated = true;
                    }

                    if(request.getActionTakenDetails() != null && !request.getActionTakenDetails().equals(report.getActionTakenDetails())){
                        report.setActionTakenDetails(request.getActionTakenDetails());
                        updated = true;
                    }

                    if(updated){
                        report.setLastModifiedAt(Instant.now());
                        return contentReportRepository.save(report)
                                .flatMap(this::mapToResponse);
                    }
                    return Mono.just(report).flatMap(this::mapToResponse);
                },
                List.of(
                        new ValidationRule(
                                ContentReportEntity::isResolvedOrDismissed,
                                "Cannot update reports that are resolved or dismissed",
                                ErrorCode.VALIDATION_FAILED
                        ),
                        new ValidationRule(
                                report -> !report.isUnderReview() && !report.isEscalated(),
                                "Only reports under review or escalated can be updated",
                                ErrorCode.VALIDATION_FAILED
                        ),
                        new ValidationRule(
                                report -> report.isUnderReview() && !isAdmin && report.isAssignedToSomeoneElse(moderatorId),
                                "Cannot update reports assigned to another moderator",
                                ErrorCode.FORBIDDEN
                        ),
                        new ValidationRule(
                                report -> report.isEscalated() && !isAdmin,
                                "Only admins can update escalated reports",
                                ErrorCode.FORBIDDEN
                        )
                ))

        );
    }

    // ==================== USER REPORT HISTORY ====================

    @Override
    public Mono<UserReportHistoryResponse> getOwnReportHistory(ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());
        return getUserReportHistoryInternal(userId);
    }

    @Override
    public Mono<UserReportHistoryResponse> getUserReportHistory(UUID userId, ViewerContext viewerContext){
        if(!viewerContext.isModeratorOrAdmin()){
            return Mono.error(new ApiException("Only moderators can view others user's report history", ErrorCode.FORBIDDEN));
        }

        return getUserReportHistoryInternal(userId);
    }


    // ==================== REFERENCE DATA ====================

    @Override
    public Flux<ReportTemplateResponse> getReportTemplates() {
        return reportTemplateRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .flatMap(this::mapToReportTemplateResponse);
    }

    @Override
    public Flux<ReportTemplateResponse> getReportTemplatesByCategory(ReportCategory category) {
    return reportTemplateRepository.findByReportCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category)
            .flatMap(this::mapToReportTemplateResponse);
    }

    @Override
    public Flux<ModerationActionTemplateResponse> getModerationActionTemplates(ViewerContext viewerContext){

        if(!viewerContext.isModeratorOrAdmin()){
            return Flux.error(new ApiException("Access denied. Only moderators can view moderation action templates", ErrorCode.FORBIDDEN));
        }

        return moderationActionTemplateRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .filter(template -> viewerContext.isInGroup(template.getActionType().getRequiredGroup()))
                .flatMap(this::mapToModerationActionTemplateResponse);
    }

    @Override
    public Flux<DismissalReasonTemplateResponse> getDismissalReasonTemplates(ViewerContext viewerContext){

        if(!viewerContext.isModeratorOrAdmin()){
            return Flux.error(new ApiException("Access denied. Only moderators can view moderation action templates", ErrorCode.FORBIDDEN));
        }

        return dismissalReasonTemplateRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .flatMap(this::mapToDismissalReasonTemplateResponse);
    }


    // ==================== PRIVATE HELPERS ====================

    private Mono<ContentReportEntity> findReport(UUID reportId){
        return contentReportRepository.findById(reportId)
                .switchIfEmpty(Mono.error(new ApiException("Report not found", ErrorCode.RESOURCE_NOT_FOUND)));
    }

    private record ValidationRule(Predicate<ContentReportEntity> condition, String errorMessage, ErrorCode errorCode){
        // Convenience constructor for VALIDATION_FAILED
        public ValidationRule(Predicate<ContentReportEntity> condition, String errorMessage){
            this(condition, errorMessage, ErrorCode.VALIDATION_FAILED);
        }
    };

    private <T> Mono<T> performModeratorAction(
            UUID reportId,
            ViewerContext viewerContext,
            String actionDescription,
            Function<ContentReportEntity, Mono<T>> action,
            List<ValidationRule> validators
    ){
        if(!viewerContext.isModeratorOrAdmin()){
            return Mono.error(new ApiException("Only moderators can " + actionDescription, ErrorCode.FORBIDDEN));
        }

        return findReport(reportId)
                .flatMap(report -> {
                    for(ValidationRule rule: validators){
                        // Condition is FAILURE condition - if true, we error
                        if(rule.condition().test(report)){
                            return Mono.error(new ApiException(rule.errorMessage, rule.errorCode));
                        }
                    }
                    return action.apply(report);
                })
                .as(transactionalOperator::transactional);
    }

    private Mono<Void> validateReportTarget(CreateReportRequest request) {
        return switch (request.getTargetType()) {
            case POST -> {
                if (request.getPostId() == null) {
                    yield Mono.error(new ApiException("Post Id is required for post reports", ErrorCode.VALIDATION_FAILED));
                }
                yield postRepository.findByIdAndIsDeletedFalse(request.getPostId())
                        .switchIfEmpty(Mono.error(new ApiException("Post not found", ErrorCode.RESOURCE_NOT_FOUND)))
                        .then();
            }
            case THREAD -> {
                if (request.getThreadId() == null) {
                    yield Mono.error(new ApiException("Thread Id is required for thread reports", ErrorCode.VALIDATION_FAILED));
                }
                yield forumThreadRepository.findByIdAndIsDeletedFalse(request.getThreadId())
                        .switchIfEmpty(Mono.error(new ApiException("Thread not found", ErrorCode.RESOURCE_NOT_FOUND)))
                        .then();
            }
            case USER -> {
                if (request.getReportedUserId() == null) {
                    yield Mono.error(new ApiException("User Id is required for user reports", ErrorCode.VALIDATION_FAILED));
                }
                yield appUserRepository.findAppUserByKeycloakId(request.getReportedUserId().toString())
                        .switchIfEmpty(Mono.error(new ApiException("User not found", ErrorCode.RESOURCE_NOT_FOUND)))
                        .then();
            }
            default -> Mono.error(new ApiException("Invalid target type", ErrorCode.VALIDATION_FAILED));
        };
    }

    private Mono<ReportResponse> validateNotDuplicateReport(UUID reporterId, CreateReportRequest request) {
        return contentReportRepository.hasUserReportedTarget(
                reporterId,
                request.getPostId(),
                request.getThreadId(),
                request.getReportedUserId())
                .flatMap(exists -> {
                    if(exists){
                        return Mono.error(new ApiException("You have already reported this content", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<ContentReportEntity> createReportEntity(UUID reporterId, CreateReportRequest request) {
        ContentReportEntity report = ContentReportEntity.builder()
                .reporterId(reporterId)
                .isAnonymous(request.getIsAnonymous())
                .targetType(request.getTargetType())
                .threadId(request.getThreadId())
                .postId(request.getPostId())
                .reportedUserId(request.getReportedUserId())
                .reportCategory(request.getReportCategory())
                .severity(request.getSeverity())
                .reason(request.getReason())
                .details(request.getDetails())
                .status(ReportStatus.PENDING)
                .reportedAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        return contentReportRepository.save(report);
    }

    private Mono<ReportResponse> mapToResponse(ContentReportEntity report) {
        return Mono.just(ReportResponse.builder()
                        .id(report.getId())
                        .reporterId(report.getIsAnonymous()? null : report.getReporterId())
                        .targetType(report.getTargetType())
                        .threadId(report.getThreadId())
                        .postId(report.getPostId())
                        .reportedUserId(report.getReportedUserId())
                        .reportCategory(report.getReportCategory())
                        .severity(report.getSeverity())
                        .reason(report.getReason())
                        .details(report.getDetails())
                        .status(report.getStatus())
                        .assignedModeratorId(report.getAssignedModeratorId())
                        .assignedAt(report.getAssignedAt())
                        .actionTaken(report.getActionTaken())
                        .actionTakenDetails(report.getActionTakenDetails())
                        .dismissalReason(report.getDismissalReason())
                        .resolutionNotes(report.getResolutionNotes())
                        .reviewedAt(report.getReviewedAt())
                        .reviewedBy(report.getReviewedBy())
                        .reportedAt(report.getReportedAt())
                        .lastModifiedAt(report.getLastModifiedAt())
                        .isAnonymous(report.getIsAnonymous())
                .build());
    }

    private Mono<ReportTemplateResponse> mapToReportTemplateResponse(ReportTemplateEntity template) {
        return Mono.just(ReportTemplateResponse.builder()
                        .id(template.getId())
                        .reportCategory(template.getReportCategory())
                        .templateText(template.getTemplateText())
                        .requiresDetails(template.getRequiresDetails())
                        .autoSeverity(template.getAutoSeverity())
                        .displayOrder(template.getDisplayOrder())
                .build());
    }

    private Mono<ModerationActionTemplateResponse> mapToModerationActionTemplateResponse(ModerationActionTemplateEntity template){
        return Mono.just(ModerationActionTemplateResponse.builder()
                        .actionType(template.getActionType())
                        .defaultMessage(template.getDefaultMessage())
                        .description(template.getDescription())
                        .exampleMessage(template.getExampleMessage())
                .build());
    }

    private Mono<DismissalReasonTemplateResponse> mapToDismissalReasonTemplateResponse(DismissalReasonTemplateEntity template){
        return Mono.just(DismissalReasonTemplateResponse.builder()
                        .reasonCode(template.getReasonCode())
                        .defaultMessage(template.getDefaultMessage())
                        .description(template.getDescription())
                        .exampleMessage(template.getExampleMessage())
                .build());
    }

    private Mono<PaginatedResponse<ReportResponse>> getReportsWithFilters(
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
            String search,
            String sortBy,
            String sortDirection) {

        if(page < 0 || size <= 0){
            throw new InvalidPaginationException();
        }

        int offset = page * size;

        String targetTypeStr = targetType != null? targetType.name() : null;
        String statusStr = status != null ? status.name() : null;
        String categoryStr = category != null ? category.name() : null;
        String severityStr = severity != null ? severity.name() : null;
        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();

        String effectiveSortBy = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = determineSortDirection(sortDirection, effectiveSortBy);

        return contentReportRepository.findAllReportsPaginated(
                        reporterId, reportedUserId, threadId, postId, targetTypeStr,
                        statusStr, categoryStr, severityStr, assignedTo, effectiveSearch,
                        effectiveSortBy, effectiveSortDirection, size, offset
                ).flatMap(this::mapToResponse)
                .collectList()
                .zipWith(contentReportRepository.countAllReportsWithFilters(
                        reporterId, reportedUserId, threadId, postId, targetTypeStr,
                        statusStr, categoryStr, severityStr, assignedTo, effectiveSearch
                ))
                .map(tuple -> new PaginatedResponse<>(tuple.getT1(), page, size, tuple.getT2()));

    }

    private String validateAndNormalizeSortBy(String sortBy) {
        Set<String> allowedFields = Set.of("severity", "reported_at");
        if(sortBy == null || !allowedFields.contains(sortBy)){
            return "reported_at"; // Default to most recent
        }
        return sortBy;
    }

    private String determineSortDirection(String sortDirection, String sortBy) {
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        }
        // Natural defaults
        return switch (sortBy) {
            case "reported_at", "last_modified_at" -> "DESC";  // Newest first
            case "severity" -> "ASC";  // Critical first
            default -> "ASC";
        };
    }

    private Mono<UserReportHistoryResponse> getUserReportHistoryInternal(UUID userId){
        return userReportHistoryRepository.findByUserId(userId)
                .switchIfEmpty(Mono.just(UserReportHistoryEntity.builder()
                                .userId(userId)
                                .totalReportsMade(0)
                                .reportsUpheld(0)
                                .reportsDismissed(0)
                                .lastReportAt(null)
                                // TODO: Future - report ban functionality
                                // When implemented, default should be false
                                .isReportBanned(false)
                                .createdAt(Instant.now())
                        .build()))
                .map(this::mapToHistoryResponse);
    }

    private UserReportHistoryResponse mapToHistoryResponse(UserReportHistoryEntity reportHistory) {
        double accuracyRate = 0.0;
        if(reportHistory.getTotalReportsMade() > 0){
            accuracyRate = (double) reportHistory.getReportsUpheld() / reportHistory.getTotalReportsMade() * 100;
        }

        return UserReportHistoryResponse.builder()
                .userId(reportHistory.getUserId())
                .totalReportsMade(reportHistory.getTotalReportsMade())
                .reportsUpheld(reportHistory.getReportsUpheld())
                .reportsDismissed(reportHistory.getReportsDismissed())
                .accuracyRate(Math.round(accuracyRate * 100.0) / 100.0)
                .lastReportAt(reportHistory.getLastReportAt())
                .isReportBanned(reportHistory.getIsReportBanned())
                // TODO: Implement report ban functionality in future phase
                // - Admins can ban users from submitting reports
                // - Banned users cannot create new reports
                // - Ban can have expiration (reportBanUntil)
                // - Ban reason stored for audit
                .reportBanUntil(reportHistory.getReportBanUntil())
                .reportBanReason(reportHistory.getReportBanReason())
                .createdAt(reportHistory.getCreatedAt())
                .build();
    }


}
