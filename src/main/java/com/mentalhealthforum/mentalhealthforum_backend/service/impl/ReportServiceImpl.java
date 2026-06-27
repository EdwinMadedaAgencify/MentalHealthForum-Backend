package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.contentReportsComprehensiveSafety.*;
import com.mentalhealthforum.mentalhealthforum_backend.dto.threadLifecycleAndMetadata.ThreadDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.user.UserDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterMetadata;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterOption;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.ReportFilterDto;
import com.mentalhealthforum.mentalhealthforum_backend.enums.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.listings.ReportSortField;
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
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


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
    private final ThreadRepository threadRepository;
    private final AppUserRepository appUserRepository;

    public ReportServiceImpl(
            TransactionalOperator transactionalOperator,
            ContentReportRepository contentReportRepository,
            ReportTemplateRepository reportTemplateRepository,
            ModerationActionTemplateRepository moderationActionTemplateRepository,
            DismissalReasonTemplateRepository dismissalReasonTemplateRepository,
            UserReportHistoryRepository userReportHistoryRepository,
            PostRepository postRepository,
            ThreadRepository threadRepository,
            AppUserRepository appUserRepository) {
        this.transactionalOperator = transactionalOperator;
        this.contentReportRepository = contentReportRepository;
        this.reportTemplateRepository = reportTemplateRepository;
        this.moderationActionTemplateRepository = moderationActionTemplateRepository;
        this.dismissalReasonTemplateRepository = dismissalReasonTemplateRepository;
        this.userReportHistoryRepository = userReportHistoryRepository;
        this.postRepository = postRepository;
        this.threadRepository = threadRepository;
        this.appUserRepository = appUserRepository;
    }

    // ==================== USER ACTIONS ====================

    @Override
    public Mono<ReportResponse> createThreadReport(CreateThreadReportRequest request, ViewerContext viewerContext) {

        // TODO: Future feature - Add moderator reporting validations
        // 1. validateNotSelfReport(request, viewerContext)
        // 2. validateAndEscalateModeratorReport(request, viewerContext)
        // 3. validateModeratorReportingModerator(request, viewerContext)
        // See: Feature branch 'moderator-report-handling'

        UUID reporterId = UUID.fromString(viewerContext.getUserId());
        // Validate target exists
        return validateThreadExists(request.getThreadId())
                .flatMap(thread -> resolveReportedUser(request, thread))
                .flatMap(targetUserId ->
                    validateNotDuplicateReport(reporterId, request.getThreadId(), null, targetUserId)
                            .thenReturn(targetUserId)
                )
                .flatMap(targetUserId -> createThreadReportEntity(reporterId, request, targetUserId))
                .flatMap(this::enrichSingleReportWithData)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ReportResponse> createPostReport(CreatePostReportRequest request, ViewerContext viewerContext) {

        UUID reporterId = UUID.fromString(viewerContext.getUserId());
        // TODO: Future feature - Add moderator reporting validations
        // 1. validateNotSelfReport(request, viewerContext)
        // 2. validateAndEscalateModeratorReport(request, viewerContext)
        // 3. validateModeratorReportingModerator(request, viewerContext)
        // See: Feature branch 'moderator-report-handling'


        // Validate target exists
        return validateAndCreatePostReport(reporterId, request)
                .flatMap(this::enrichSingleReportWithData)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ReportResponse> createUserReport(CreateUserReportRequest request, ViewerContext viewerContext) {

        UUID reporterId = UUID.fromString(viewerContext.getUserId());
        // TODO: Future feature - Add moderator reporting validations
        // 1. validateNotSelfReport(request, viewerContext)
        // 2. validateAndEscalateModeratorReport(request, viewerContext)
        // 3. validateModeratorReportingModerator(request, viewerContext)
        // See: Feature branch 'moderator-report-handling'

        // Validate target exists
        return validateUserExists(request.getReportedUserId())
                .then(validateNotDuplicateReport(reporterId, null, null, request.getReportedUserId()))
                .then(createUserReportEntity(reporterId, request))
                .flatMap(this::enrichSingleReportWithData)
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

        if(page < 0 || size <= 0){
            throw new InvalidPaginationException();
        }

        int offset = page * size;

        UUID userId = UUID.fromString(viewerContext.getUserId());
        String targetTypeStr = targetType != null? targetType.name() : null;
        String statusStr = status != null ? status.name() : null;
        String categoryStr = category != null ? category.name() : null;
        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();

        ReportSortField sortByField = ReportSortField.fromString(sortBy);
        String effectiveSortDirection = sortByField.determineSortDirection(sortDirection);

        return contentReportRepository.findOwnReportsPaginated(
                        userId, targetTypeStr,
                        statusStr, categoryStr, effectiveSearch,
                        sortByField.getValue(), effectiveSortDirection, size, offset
                )
                .collectList()
                .zipWith(contentReportRepository.countOwnReportsWithFilters(
                        userId, targetTypeStr,
                        statusStr, categoryStr, effectiveSearch
                ))
                .flatMap(tuple -> {
                    List<ContentReportEntity> reports = tuple.getT1();
                    long total = tuple.getT2();

                    if(reports.isEmpty()){
                        return Mono.just(new PaginatedResponse<>(List.of(), page, size, 0L));
                    }

                    return enrichReportsWithBatchData(reports)
                            .map(enrichedReports -> {

                                FilterMetadata<ReportFilterDto> filters = FilterMetadata.<ReportFilterDto>builder()
                                        .filters(null)
                                        .sortOptions(ReportSortField.getOwnReportsSortOptions())
                                        .build();
                                return  new PaginatedResponse<>(enrichedReports.responses, page, size, total, filters);

                            });
                });

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
            UUID reviewedBy,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext) {

        if(!viewerContext.isModeratorOrAdmin()){
            return Mono.error(new ApiException("Only moderators can view all reports", ErrorCode.FORBIDDEN));
        }

        if(page < 0 || size <= 0){
            throw new InvalidPaginationException();
        }

        int offset = page * size;

        String targetTypeStr = targetType != null? targetType.name() : null;
        String statusStr = status != null ? status.name() : null;
        String categoryStr = category != null ? category.name() : null;
        String severityStr = severity != null ? severity.name() : null;
        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();

        ReportSortField sortByField = ReportSortField.fromString(sortBy);
        String effectiveSortDirection = sortByField.determineSortDirection(sortDirection);

        return contentReportRepository.findAllReportsPaginated(
                        reporterId, reportedUserId, threadId, postId, targetTypeStr,
                        statusStr, categoryStr, severityStr, assignedTo,reviewedBy, effectiveSearch,
                        sortByField.getValue(), effectiveSortDirection, size, offset
                )
                .collectList()
                .zipWith(contentReportRepository.countAllReportsWithFilters(
                        reporterId, reportedUserId, threadId, postId, targetTypeStr,
                        statusStr, categoryStr, severityStr, assignedTo, reviewedBy, effectiveSearch
                ))
                .flatMap(tuple -> {
                    List<ContentReportEntity> reports = tuple.getT1();
                    long total = tuple.getT2();

                    if(reports.isEmpty()){
                        return Mono.just(new PaginatedResponse<>(List.of(), page, size, 0L));
                    }

                    return enrichReportsWithBatchData(reports)
                            .map(enrichedReports -> {

                                FilterMetadata<ReportFilterDto> filters = buildReportFilters(enrichedReports);
                                return  new PaginatedResponse<>(enrichedReports.responses, page, size, total, filters);

                            });
                });

    }

    @Override
    public Mono<ReportResponse> getReportById(UUID reportId, ViewerContext viewerContext) {

        UUID userId = UUID.fromString(viewerContext.getUserId());

        return findReport(reportId)
                .flatMap(report -> {
                  if(!report.getReporterId().equals(userId) && !viewerContext.isModeratorOrAdmin()){
                        return Mono.error(new ApiException("You can only view your own reports", ErrorCode.FORBIDDEN));
                    }
                    return enrichSingleReportWithData(report);
                });
    }

    @Override
    public Mono<ReportResponse> assignReport(UUID reportId, UUID moderatorId, ViewerContext viewerContext) {
        // TODO: Future feature - Add moderator-specific validation
        // See: Feature branch 'moderator-report-handling'

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
                                        flatMap(this::enrichSingleReportWithData);
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
                                        .flatMap(this::enrichSingleReportWithData);

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
                            .flatMap(this::enrichSingleReportWithData);
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
                            .flatMap(this::enrichSingleReportWithData);
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
                                .flatMap(this::enrichSingleReportWithData);
                    }
                    return Mono.just(report).flatMap(this::enrichSingleReportWithData);
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
                    // TODO: Future feature - Restrict moderator actions on moderator reports
                    // If report.reportedUserId is a moderator, only admins can act
                    // See: Feature branch 'moderator-report-handling'

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

    private Mono<ThreadEntity> validateThreadExists(UUID threadId){
        return threadRepository.findByIdAndIsDeletedFalse(threadId)
                .switchIfEmpty(Mono.error(new ApiException("Thread not found", ErrorCode.RESOURCE_NOT_FOUND)));
    }

    private Mono<UUID> resolveReportedUser(CreateThreadReportRequest request, ThreadEntity thread){
        UUID targetUserId = request.getReportedUserId();

        if(targetUserId == null){
            return Mono.just(thread.getCreatorId());
        }

        return validateUserInThread(targetUserId, thread.getId())
                .thenReturn(targetUserId);
    }

    private Mono<Void> validateUserInThread(UUID userId, UUID threadId){
        // check if the user is the creator
        return postRepository.isUserInThread(threadId, userId)
                .flatMap(isInThread -> {
                    if(isInThread){
                        return Mono.empty();
                    }
                    return Mono.error(new ApiException(
                            "User is not part of this thread discussion",
                            ErrorCode.VALIDATION_FAILED
                    ));
                });
    }

    private Mono<ContentReportEntity> validateAndCreatePostReport(UUID reporterId, CreatePostReportRequest request){
        return postRepository.findByIdAndIsDeletedFalse(request.getPostId())
                .switchIfEmpty(Mono.error(new ApiException("Post not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(post ->
                        // TODO: Future feature - Add moderator reporting validations here too
                        // validateReportTarget(request, viewerContext) will be called from the caller
                        validateNotDuplicateReport(reporterId, null, request.getPostId(), null)
                                .then(createPostReportEntity(reporterId, request, post))
                );
    }

    public Mono<Void> validateUserExists(UUID userId){
        return appUserRepository.findAppUserByKeycloakId(userId.toString())
                .switchIfEmpty(Mono.error(new ApiException("User not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .then();
    }

    private Mono<Void> validateNotDuplicateReport(UUID reporterId, UUID threadId, UUID postId, UUID reportedUserId) {
        return contentReportRepository.findActiveReportForTarget(
                reporterId,
                postId,
                threadId,
                reportedUserId)
                .flatMap(report -> {
                    String message = switch(report.getStatus()){
                        case PENDING -> "You have already reported this content. It is pending review";
                        case UNDER_REVIEW -> "You have already reported this content. It is currently being reviewed by a moderator.";
                        default -> "You have already reported this content.";
                    };

                        return Mono.error(new ApiException(message, ErrorCode.VALIDATION_FAILED));
                })
                .then();
    }

    private Mono<ContentReportEntity> createThreadReportEntity(UUID reporterId, CreateThreadReportRequest request, UUID targetUserId) {
        ContentReportEntity report = ContentReportEntity.builder()
                // Static data (provided at report time)
                .reporterId(reporterId)
                .isAnonymous(request.getIsAnonymous())
                .targetType(ReportTargetType.THREAD)
                .threadId(request.getThreadId())
                .reportedUserId(targetUserId)

                // Report metadata (provided by reporter)
                .reportCategory(request.getReportCategory())
                .severity(request.getSeverity())
                .reason(request.getReason())
                .details(request.getDetails())

                // System metadata
                .status(ReportStatus.PENDING)
                .reportedAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        return contentReportRepository.save(report);
    }

    private Mono<ContentReportEntity> createPostReportEntity(UUID reporterId, CreatePostReportRequest request, PostEntity post) {
        ContentReportEntity report = ContentReportEntity.builder()
                // Static data (provided at report time)
                .reporterId(reporterId)
                .isAnonymous(request.getIsAnonymous())
                .targetType(ReportTargetType.POST)
                .postId(request.getPostId())

                //  SNAPSHOTS - Capture the state at reporting time
                .postContent(post.getContent()) // What was said
                .threadId(post.getThreadId()) // Where it was said
                .reportedUserId(post.getAuthorId()) // Who said it

                // Report metadata (provided by reporter)
                .reportCategory(request.getReportCategory())
                .severity(request.getSeverity())
                .reason(request.getReason())
                .details(request.getDetails())

                // System metadata
                .status(ReportStatus.PENDING)
                .reportedAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        return contentReportRepository.save(report);
    }


    private Mono<ContentReportEntity> createUserReportEntity(UUID reporterId, CreateUserReportRequest request) {
        // For non-POST reports
        ContentReportEntity report = ContentReportEntity.builder()
                // Static data (provided at report time)
                .reporterId(reporterId)
                .isAnonymous(request.getIsAnonymous())
                .targetType(ReportTargetType.USER)
                .reportedUserId(request.getReportedUserId())

                // Report metadata (provided by reporter)
                .reportCategory(request.getReportCategory())
                .severity(request.getSeverity())
                .reason(request.getReason())
                .details(request.getDetails())

                // System metadata
                .status(ReportStatus.PENDING)
                .reportedAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        return contentReportRepository.save(report);
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

    /**
     * Helper method to wrap optional Mono values in Optional.
     * Avoids null warnings and handles optional data gracefully.
     */
    private<T> Mono<Optional<T>> optionalMono(Supplier<Mono<T>> supplier, boolean shouldFetch){
        if(!shouldFetch){
            return Mono.just(Optional.empty());
        }
        return supplier.get()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    /**
     * Enriches a single report with reporter, reported user, thread, assigned moderator, reviewed by details.
     * Uses individual fetches since only one report is being enriched.
     */
    private Mono<ReportResponse> enrichSingleReportWithData(ContentReportEntity report){

        // Fetch reporter (if not anonymous)
        Mono<UserDetails> reporterMono = report.getIsAnonymous()
                ? Mono.just(AppUserEntity.defaultUser())
                : appUserRepository.findAppUserByKeycloakId(report.getReporterId().toString())
                  .map(AppUserEntity::toUserDetails)
                  .defaultIfEmpty(AppUserEntity.defaultUser());


        // Fetch reported user (if exists)
        Mono<UserDetails> reportedUserMono = report.getReportedUserId() != null
                ? appUserRepository.findAppUserByKeycloakId(report.getReportedUserId().toString())
                .map(AppUserEntity::toUserDetails)
                  .defaultIfEmpty(AppUserEntity.defaultUser())
                : Mono.just(AppUserEntity.defaultUser());


        // Fetch thread (if exists)
        Mono<ThreadDetails> threadMono = report.getThreadId() != null
                ? threadRepository.findById(report.getThreadId())
                  .map(ThreadEntity::toThreadDetails)
                : Mono.just(ThreadEntity.defaultThread());

        // Fetch assigned moderator (if exists)
        Mono<UserDetails> assignedModeratorMono = report.getAssignedModeratorId() != null
                ? appUserRepository.findAppUserByKeycloakId(report.getAssignedModeratorId().toString())
                .map(AppUserEntity::toUserDetails)
                .defaultIfEmpty(AppUserEntity.defaultUser())
                : Mono.just(AppUserEntity.defaultUser());


        // Fetch reviewed by (if exists)
        Mono<UserDetails> reviewedByMono = report.getReviewedBy() != null
                ? appUserRepository.findAppUserByKeycloakId(report.getReviewedBy().toString())
                .map(AppUserEntity::toUserDetails)
                .defaultIfEmpty(AppUserEntity.defaultUser())
                : Mono.just(AppUserEntity.defaultUser());

        return Mono.zip(
                reporterMono,
                reportedUserMono,
                threadMono,
                assignedModeratorMono,
                reviewedByMono
        ).map(tuple -> mapToTypedResponseWithData(
                report,
                tuple.getT1(), // reporter
                tuple.getT2(), // reportedUser
                tuple.getT3(), // thread
                tuple.getT4(), // assignedModerator
                tuple.getT5()  // reviewedBy
        ));
    }


    private Mono<EnrichedReportData> enrichReportsWithBatchData(List<ContentReportEntity> reports) {
        if(reports.isEmpty()){
            return Mono.just(new EnrichedReportData(
                   List.of(),
                   List.of(),
                   Map.of(),
                   Map.of(),
                   Map.of(),
                   Map.of(),
                   Map.of()
            ));
        }

        // Extract IDs
        List<UUID> reporterIds = reports.stream()
                .map(ContentReportEntity::getReporterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<UUID> reportedUserIds = reports.stream()
                .map(ContentReportEntity::getReportedUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<UUID> threadIds = reports.stream()
                .map(ContentReportEntity::getThreadId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<UUID> assignedModeratorIds = reports.stream()
                .map(ContentReportEntity::getAssignedModeratorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<UUID> reviewerIds = reports.stream()
                .map(ContentReportEntity::getReviewedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Batch fetch reporters
        Mono<Map<UUID, UserDetails>> reportersMap = reporterIds.isEmpty()
                ? Mono.just(Map.of())
                : appUserRepository
                .findAppUsersByKeycloakIds(reporterIds)
                .collectMap(AppUserEntity::getKeycloakId, AppUserEntity::toUserDetails);

        // Batch fetch reported users
        Mono<Map<UUID, UserDetails>> reportedUserMap = reportedUserIds.isEmpty()
                ? Mono.just(Map.of())
                : appUserRepository
                .findAppUsersByKeycloakIds(reportedUserIds)
                .collectMap(AppUserEntity::getKeycloakId, AppUserEntity::toUserDetails);

        // Batch fetch threads
        Mono<Map<UUID, ThreadDetails>> threadsMap = threadIds.isEmpty()
                ? Mono.just(Map.of())
                : threadRepository
                .findThreadsByIds(threadIds)
                .collectMap(ThreadEntity::getId, ThreadEntity::toThreadDetails);

        // Batch fetch assigned moderators
        Mono<Map<UUID, UserDetails>> assignedModeratorsMap = assignedModeratorIds.isEmpty()
                ? Mono.just(Map.of())
                : appUserRepository
                .findAppUsersByKeycloakIds(assignedModeratorIds)
                .collectMap(AppUserEntity::getKeycloakId, AppUserEntity::toUserDetails);

        // Batch fetch reviewers
        Mono<Map<UUID, UserDetails>> reviewersMap = reviewerIds.isEmpty()
                ? Mono.just(Map.of())
                : appUserRepository
                .findAppUsersByKeycloakIds(reviewerIds)
                .collectMap(AppUserEntity::getKeycloakId, AppUserEntity::toUserDetails);

        return Mono.zip(
                reportersMap,
                reportedUserMap,
                threadsMap,
                assignedModeratorsMap,
                reviewersMap
        ).map(tuple -> {
            Map<UUID, UserDetails> reporters = tuple.getT1();
            Map<UUID, UserDetails> reportedUsers = tuple.getT2();
            Map<UUID, ThreadDetails> threads = tuple.getT3();
            Map<UUID, UserDetails> assignedModerators = tuple.getT4();
            Map<UUID, UserDetails> reviewers = tuple.getT5();

            List<ReportResponse> responses = reports.stream()
                    .map(report -> mapToTypedResponseWithData(
                            report,

                            report.getReporterId() != null
                                ? reporters.getOrDefault(report.getReporterId(), AppUserEntity.defaultUser())
                                : AppUserEntity.defaultUser(),

                            report.getReportedUserId() != null
                                ? reportedUsers.getOrDefault(report.getReportedUserId(), AppUserEntity.defaultUser())
                                : AppUserEntity.defaultUser(),

                            report.getThreadId() != null
                                ? threads.getOrDefault(report.getThreadId(), ThreadEntity.defaultThread())
                                : ThreadEntity.defaultThread(),

                            report.getAssignedModeratorId() != null
                                ? assignedModerators.getOrDefault(report.getAssignedModeratorId(), AppUserEntity.defaultUser())
                                : AppUserEntity.defaultUser(),

                            report.getReviewedBy()  != null
                                ? reviewers.getOrDefault(report.getReviewedBy(), AppUserEntity.defaultUser())
                                : AppUserEntity.defaultUser()

                    ))
                    .toList();

            return new EnrichedReportData(
                    responses,
                    reports,
                    reporters,
                    reportedUsers,
                    threads,
                    assignedModerators,
                    reviewers
            );

        });
    }

    /**
     * Maps a report to a response WITH enrichment data.
     */

    private ReportResponse mapToTypedResponseWithData(
            ContentReportEntity report,
            UserDetails reporter,
            UserDetails reportedUser,
            ThreadDetails thread,
            UserDetails assignedModerator,
            UserDetails reviewer
    ) {
        UUID reporterId = report.getReporterId();
        String reporterDisplayName = reporter.getDisplayName();
        String reporterAvatarUrl = reporter.getAvatarUrl();

        if(report.getIsAnonymous()){
            reporterId = null;
            reporterDisplayName = null;
            reporterAvatarUrl = null;
        }

        return switch (report.getTargetType()){
            case THREAD -> ThreadReportResponse.builder()
                    .id(report.getId())

                    // Reporter info
                    .reporterId(reporterId)
                    .reporterDisplayName(reporterDisplayName)
                    .reporterAvatarUrl(reporterAvatarUrl)

                    // Target info
                    .targetType(report.getTargetType())
                    .threadId(report.getThreadId())
                    .threadTitle(thread.getTitle())


                    // Report details
                    .reportCategory(report.getReportCategory())
                    .severity(report.getSeverity())
                    .reason(report.getReason())
                    .details(report.getDetails())
                    .status(report.getStatus())

                    // Moderation info
                    .assignedModeratorId(report.getAssignedModeratorId())
                    .assignedModeratorDisplayName(assignedModerator.getDisplayName())
                    .assignedModeratorAvatarUrl(assignedModerator.getAvatarUrl())
                    .assignedAt(report.getAssignedAt())
                    .actionTaken(report.getActionTaken())
                    .actionTakenDetails(report.getActionTakenDetails())
                    .dismissalReason(report.getDismissalReason())
                    .resolutionNotes(report.getResolutionNotes())
                    .reviewedAt(report.getReviewedAt())
                    .reviewedBy(report.getReviewedBy())
                    .reviewedByDisplayName(reviewer.getDisplayName())
                    .reviewedByAvatarUrl(reviewer.getAvatarUrl())

                    // Timestamps
                    .reportedAt(report.getReportedAt())
                    .lastModifiedAt(report.getLastModifiedAt())
                    .isAnonymous(report.getIsAnonymous())
                    .build();

            case POST -> PostReportResponse.builder()
                    .id(report.getId())

                    // Reporter info
                    .reporterId(reporterId)
                    .reporterDisplayName(reporterDisplayName)
                    .reporterAvatarUrl(reporterAvatarUrl)

                    // Target info
                    .targetType(report.getTargetType())
                    .postId(report.getPostId())
                    .postContent(report.getPostContent())
                    // Thread context - optional, but if threadId exists, thread should exist
                    .threadId(report.getThreadId())
                    .threadTitle(thread != null? thread.getTitle() : null)


                    // Reported user - OPTIONAL for POST reports
                    .reportedUserId(report.getReportedUserId())
                    .reportedUserDisplayName(reportedUser.getDisplayName())
                    .reportedUserAvatarUrl(reportedUser.getAvatarUrl())

                    // Report details
                    .reportCategory(report.getReportCategory())
                    .severity(report.getSeverity())
                    .reason(report.getReason())
                    .details(report.getDetails())
                    .status(report.getStatus())

                    // Moderation info
                    .assignedModeratorId(report.getAssignedModeratorId())
                    .assignedModeratorDisplayName(assignedModerator.getDisplayName())
                    .assignedModeratorAvatarUrl(assignedModerator.getAvatarUrl())
                    .assignedAt(report.getAssignedAt())
                    .actionTaken(report.getActionTaken())
                    .actionTakenDetails(report.getActionTakenDetails())
                    .dismissalReason(report.getDismissalReason())
                    .resolutionNotes(report.getResolutionNotes())
                    .reviewedAt(report.getReviewedAt())
                    .reviewedBy(report.getReviewedBy())
                    .reviewedByDisplayName(reviewer.getDisplayName())
                    .reviewedByAvatarUrl(reviewer.getAvatarUrl())

                    // Timestamps
                    .reportedAt(report.getReportedAt())
                    .lastModifiedAt(report.getLastModifiedAt())
                    .isAnonymous(report.getIsAnonymous())
                    .build();

            case USER -> UserReportResponse.builder()
                    .id(report.getId())

                    // Reporter info
                    .reporterId(reporterId)
                    .reporterDisplayName(reporterDisplayName)
                    .reporterAvatarUrl(reporterAvatarUrl)

                    // Target info
                    .targetType(report.getTargetType())

                    // User fields - REQUIRED
                    .reportedUserId(report.getReportedUserId())
                    .reportedUserDisplayName(reportedUser.getDisplayName())
                    .reportedUserAvatarUrl(reportedUser.getAvatarUrl())

                    // Report details
                    .reportCategory(report.getReportCategory())
                    .severity(report.getSeverity())
                    .reason(report.getReason())
                    .details(report.getDetails())
                    .status(report.getStatus())

                    // Moderation info
                    .assignedModeratorId(report.getAssignedModeratorId())
                    .assignedModeratorDisplayName(assignedModerator.getDisplayName())
                    .assignedModeratorAvatarUrl(assignedModerator.getAvatarUrl())
                    .assignedAt(report.getAssignedAt())
                    .actionTaken(report.getActionTaken())
                    .actionTakenDetails(report.getActionTakenDetails())
                    .dismissalReason(report.getDismissalReason())
                    .resolutionNotes(report.getResolutionNotes())
                    .reviewedAt(report.getReviewedAt())
                    .reviewedBy(report.getReviewedBy())
                    .reviewedByDisplayName(reviewer.getDisplayName())
                    .reviewedByAvatarUrl(reviewer.getAvatarUrl())

                    // Timestamps
                    .reportedAt(report.getReportedAt())
                    .lastModifiedAt(report.getLastModifiedAt())
                    .isAnonymous(report.getIsAnonymous())
                    .build();

        };

    }

    private record EnrichedReportData(
            List<ReportResponse> responses,
            List<ContentReportEntity> reports,
            Map<UUID, UserDetails> reporters,
            Map<UUID, UserDetails> reportedUsers,
            Map<UUID, ThreadDetails> threads,
            Map<UUID, UserDetails> assignedModerators,
            Map<UUID, UserDetails> reviewers
    ){}

    private FilterMetadata<ReportFilterDto> buildReportFilters(EnrichedReportData data){
        // Build reporter options
        Map<UUID, Long> reporterCounts = data.reports().stream()
                .collect(Collectors.groupingBy(
                        ContentReportEntity::getReporterId,
                        Collectors.counting()
                ));

        List<FilterOption> reporterOptions = data.reporters().entrySet().stream()
                .map(entry -> {
                    UUID reporterId = entry.getKey();
                    UserDetails reporter = entry.getValue();
                    long count = reporterCounts.getOrDefault(reporterId, 0L);
                    return new FilterOption(
                            reporterId,
                            reporter.getDisplayName(),
                            reporterId.toString(),
                            reporter.getAvatarUrl(),
                            count
                    );
                })
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();

        // Build reported user options
        Map<UUID, Long> reportedUserCounts = data.reports.stream()
                .filter(report -> report.getReportedUserId() != null)
                .collect(Collectors.groupingBy(
                        ContentReportEntity::getReportedUserId,
                        Collectors.counting()
                ));

        List<FilterOption> reportedUserOptions = data.reportedUsers().entrySet().stream()
                .map(entry -> {
                    UUID reportedUserId = entry.getKey();
                    UserDetails reportedUser = entry.getValue();
                    long count = reportedUserCounts.getOrDefault(reportedUserId, 0L);
                    return FilterOption.builder()
                            .id(reportedUserId)
                            .label(reportedUser.getDisplayName())
                            .value(reportedUserId.toString())
                            .avatarUrl(reportedUser.getAvatarUrl())
                            .count(count)
                            .build();
                })
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();


        // Build thread options
        Map<UUID, Long> threadCounts = data.reports().stream()
                .filter(report -> report.getThreadId() != null)
                .collect(Collectors.groupingBy(
                        ContentReportEntity::getThreadId,
                        Collectors.counting()
                ));


        List<FilterOption> threadOptions = data.threads().entrySet().stream()
                .map(entry -> {
                    UUID threadId = entry.getKey();
                    ThreadDetails thread = entry.getValue();
                    long count = threadCounts.getOrDefault(threadId, 0L);
                    return new FilterOption(
                            threadId,
                            thread.getTitle(),
                            threadId.toString(),
                            count
                    );
                })
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();

        // Build assigned moderator options
        Map<UUID, Long> assignedModeratorCounts = data.reports().stream()
                .filter(report -> report.getAssignedModeratorId() != null)
                .collect(Collectors.groupingBy(
                        ContentReportEntity::getAssignedModeratorId,
                        Collectors.counting()
                ));

        List<FilterOption> assignedModeratorOptions = data.assignedModerators().entrySet().stream()
                .map(entry -> {
                    UUID moderatorId = entry.getKey();
                    UserDetails moderator = entry.getValue();
                    long count = assignedModeratorCounts.getOrDefault(moderatorId, 0L);
                    return new FilterOption(
                            moderatorId,
                            moderator.getDisplayName(),
                            moderatorId.toString(),
                            moderator.getAvatarUrl(),
                            count
                    );
                })
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();

        // Build reviewer options
        Map<UUID, Long> reviewerCounts = data.reports().stream()
                .filter(report -> report.getReviewedBy() != null)
                .collect(Collectors.groupingBy(
                        ContentReportEntity::getReviewedBy,
                        Collectors.counting()
                ));

        List<FilterOption> reviewerOptions = data.reviewers().entrySet().stream()
                .map(entry -> {
                    UUID reviewerId = entry.getKey();
                    UserDetails reviewer = entry.getValue();
                    long count = reviewerCounts.getOrDefault(reviewerId, 0L);
                    return new FilterOption(
                            reviewerId,
                            reviewer.getDisplayName(),
                            reviewerId.toString(),
                            reviewer.getAvatarUrl(),
                            count
                    );
                })
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();


        // Build status options
        Map<ReportStatus, Long> statusCounts = data.reports().stream()
                .collect(Collectors.groupingBy(
                        ContentReportEntity::getStatus,
                        Collectors.counting()
                ));

        List<FilterOption> statusOptions = Arrays.stream(ReportStatus.values())
                .map(status -> new FilterOption(
                    status.getDisplayName(),
                    status.name(),
                    statusCounts.getOrDefault(status, 0L)
                ))
                .filter(option -> option.getCount() > 0)
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();


        ReportFilterDto reportFilters = ReportFilterDto.builder()
                .reporters(reporterOptions)
                .reportedUsers(reportedUserOptions)
                .threads(threadOptions)
                .assignedTo(assignedModeratorOptions)
                .reviewers(reviewerOptions)
                .reportStatus(statusOptions)
                .build();

        return FilterMetadata.<ReportFilterDto>builder()
                .filters(reportFilters)
                .sortOptions(ReportSortField.getAllReportsSortOptions())
                .build();

    }

}
