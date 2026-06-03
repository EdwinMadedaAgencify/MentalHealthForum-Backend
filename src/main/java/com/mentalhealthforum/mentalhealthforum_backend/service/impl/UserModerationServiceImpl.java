package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ModerationAction;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RestrictionType;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUserEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.UserRestrictionEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.UserWarningEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserModerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class UserModerationServiceImpl implements UserModerationService {

    private static final Logger logger = LoggerFactory.getLogger(UserModerationServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final AppUserRepository appUserRepository;
    private final ForumThreadRepository forumThreadRepository;
    private final PostRepository postRepository;
    private final ContentReportRepository contentReportRepository;
    private final UserWarningRepository userWarningRepository;
    private final UserRestrictionRepository userRestrictionRepository;

    public UserModerationServiceImpl(
            TransactionalOperator transactionalOperator,
            AppUserRepository appUserRepository,
            ForumThreadRepository forumThreadRepository,
            PostRepository postRepository,
            ContentReportRepository contentReportRepository,
            UserWarningRepository userWarningRepository,
            UserRestrictionRepository userRestrictionRepository) {
        this.transactionalOperator = transactionalOperator;
        this.appUserRepository = appUserRepository;
        this.forumThreadRepository = forumThreadRepository;
        this.postRepository = postRepository;
        this.contentReportRepository = contentReportRepository;
        this.userWarningRepository = userWarningRepository;
        this.userRestrictionRepository = userRestrictionRepository;
    }

    // ==================== WARNINGS ====================

    @Override
    public Mono<UserWarningResponse> warnUser(UUID userId, WarnUserRequest request, ViewerContext viewerContext) {
        UUID moderatorId = UUID.fromString(viewerContext.getUserId());

        return ModerationAction.USER_WARNED.checkPermission(viewerContext)
                .then(validateUserExists(userId))
                .then(validateNotWarningSelf(userId, moderatorId))
                .then(validateRelatedIds(request))
                .flatMap(v -> {
                    Instant expiresAt = request.expiresInDays() != null
                            ? Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS)
                            : null;

                    UserWarningEntity warning = UserWarningEntity.builder()
                            .userId(userId)
                            .warnedBy(moderatorId)
                            .warningType(request.warningType())
                            .warningText(request.warningText())
                            .relatedPostId(request.relatedPostId())
                            .relatedThreadId(request.relatedThreadId())
                            .relatedReportId(request.relatedReportId())
                            .warnedAt(Instant.now())
                            .expiresAt(expiresAt)
                            .isActive(true)
                            .build();

                    return userWarningRepository.save(warning)
                            .flatMap(this::mapToWarningResponse);
                })
                .as(transactionalOperator::transactional);

    }



    @Override
    public Flux<UserWarningResponse> getUserWarnings(UUID userId, ViewerContext viewerContext) {
        boolean isModeratorOrAdmin = viewerContext.isModeratorOrAdmin();
        UUID requestingUserId = UUID.fromString(viewerContext.getUserId());

        // Users can only see their own warnings
        if(!isModeratorOrAdmin && !requestingUserId.equals(userId)) {
            return Flux.error(new ApiException("You can only view your own warnings", ErrorCode.FORBIDDEN));
        }

        return  validateUserExists(userId)
                .thenMany(userWarningRepository.findByUserIdOrderByWarnedAtDesc(userId))
                .flatMap(this::mapToWarningResponse);
    }

    @Override
    public Mono<Void> acknowledgeWarning(UUID warningId, ViewerContext viewerContext) {
        UUID userId = UUID.fromString(viewerContext.getUserId());
        return userWarningRepository.findById(warningId)
                .switchIfEmpty(Mono.error(new ApiException("Warning not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(warning -> {
                    if (!warning.getUserId().equals(userId)) {
                        return Mono.error(new ApiException("You can only acknowledge warnings issued to you", ErrorCode.FORBIDDEN));
                    }
                    if (warning.getAcknowledgedAt() != null) {
                        return Mono.error(new ApiException("Warning already acknowledged", ErrorCode.VALIDATION_FAILED));
                    }
                    return userWarningRepository.acknowledgeWarning(warningId);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> deactivateWarning(UUID warningId, ViewerContext viewerContext) {
        return ModerationAction.USER_WARNED.checkPermission(viewerContext)
                .then(userWarningRepository.findById(warningId)
                        .switchIfEmpty(Mono.error(new ApiException("Warning not found", ErrorCode.RESOURCE_NOT_FOUND)))
                        .flatMap(warning -> userWarningRepository.deactivateWarning(warningId))
                )
                .as(transactionalOperator::transactional);
    }

    // ==================== MUTES ====================

    @Override
    public Mono<UserRestrictionResponse> muteUser(UUID userId, MuteUserRequest request, ViewerContext viewerContext) {
        UUID moderatorId = UUID.fromString(viewerContext.getUserId());

        return ModerationAction.USER_MUTED.checkPermission(viewerContext)
                .then(validateUserExists(userId))
                .then(validateNotMutingSelf(userId, moderatorId))
                .then(validateRelatedContentReport(request.relatedReportId()))
                .flatMap(v -> checkExistingActiveRestriction(userId, RestrictionType.MUTE))
                .flatMap(hasActive -> {
                    if(hasActive){
                        return Mono.error(new ApiException("User is already muted", ErrorCode.VALIDATION_FAILED));
                    }

                    UserRestrictionEntity restriction = UserRestrictionEntity.builder()
                            .userId(userId)
                            .restrictionType(RestrictionType.MUTE)
                            .reason(request.reason())
                            .imposedBy(moderatorId)
                            .relatedReportId(request.relatedReportId())
                            .startsAt(Instant.now())
                            .expiresAt(Instant.now().plus(request.durationHours(), ChronoUnit.HOURS))
                            .isActive(true)
                            .build();

                    return userRestrictionRepository.save(restriction)
                            .flatMap(this::mapToRestrictionResponse);
                })
                .as(transactionalOperator::transactional);
    }


    @Override
    public Mono<Void> unmuteUser(UUID userId, UnMuteUserRequest request, ViewerContext viewerContext) {
        return ModerationAction.USER_UNMUTED.checkPermission(viewerContext)
                .then(findActiveRestriction(userId, RestrictionType.MUTE))
                .flatMap(restriction -> userRestrictionRepository.liftRestriction(
                        restriction.getId(),
                        UUID.fromString(viewerContext.getUserId()),
                        request.reason()
                ))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Boolean> isUserMuted(UUID userId) {
        return userRestrictionRepository.existsByUserIdRestrictionTypeAndIsActiveTrue(userId, RestrictionType.MUTE);
    }

    @Override
    public Mono<UserRestrictionResponse> getActiveMuteForUser(UUID userId, ViewerContext viewerContext){

        UUID requestingUserId = UUID.fromString(viewerContext.getUserId());
        boolean canViewAll = viewerContext.isModeratorOrAdmin();

        if(!canViewAll && !requestingUserId.equals(userId)) {
            return Mono.error(new ApiException("You can only view your own mute status", ErrorCode.FORBIDDEN));
        }

        return findActiveRestriction(userId, RestrictionType.MUTE)
                .flatMap(this::mapToRestrictionResponse);
    }

    // ==================== SUSPENSIONS ====================

    @Override
    public Mono<UserRestrictionResponse> suspendUser(UUID userId, SuspendUserRequest request, ViewerContext viewerContext) {
        UUID adminId = UUID.fromString(viewerContext.getUserId());

        return ModerationAction.USER_SUSPENDED.checkPermission(viewerContext)
                .then(validateUserExists(userId))
                .then(validateNotSuspendingSelf(userId, adminId))
                .then(validateRelatedContentReport(request.relatedReportId()))
                .flatMap(v -> checkExistingActiveRestriction(userId, RestrictionType.SUSPENSION))
                .flatMap(hasActive -> {
                    if(hasActive){
                        return Mono.error(new ApiException("User is already suspended", ErrorCode.VALIDATION_FAILED));
                    }

                    UserRestrictionEntity restriction = UserRestrictionEntity.builder()
                            .userId(userId)
                            .restrictionType(RestrictionType.SUSPENSION)
                            .reason(request.reason())
                            .imposedBy(adminId)
                            .relatedReportId(request.relatedReportId())
                            .startsAt(Instant.now())
                            .expiresAt(Instant.now().plus(request.durationDays(), ChronoUnit.DAYS))
                            .isActive(true)
                            .build();

                    return userRestrictionRepository.save(restriction)
                            .flatMap(this::mapToRestrictionResponse);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> unsuspendUser(UUID userId, UnSuspendRequest request, ViewerContext viewerContext) {
        return ModerationAction.USER_UNSUSPENDED.checkPermission(viewerContext)
                .then(findActiveRestriction(userId, RestrictionType.SUSPENSION))
                .flatMap(restriction -> userRestrictionRepository.liftRestriction(
                        restriction.getId(),
                        UUID.fromString(viewerContext.getUserId()),
                        request.reason()
                ))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Boolean> isUserSuspended(UUID userId) {
        return userRestrictionRepository.existsByUserIdRestrictionTypeAndIsActiveTrue(userId, RestrictionType.SUSPENSION);
    }

    @Override
    public Mono<UserRestrictionResponse> getActiveSuspendForUser(UUID userId, ViewerContext viewerContext){

        UUID requestingUserId = UUID.fromString(viewerContext.getUserId());
        boolean canViewAll = viewerContext.isModeratorOrAdmin();

        if(!canViewAll && !requestingUserId.equals(userId)) {
            return Mono.error(new ApiException("You can only view your own suspend status", ErrorCode.FORBIDDEN));
        }

        return findActiveRestriction(userId, RestrictionType.SUSPENSION)
                .flatMap(this::mapToRestrictionResponse);
    }

    // ==================== BANS ====================

    @Override
    public Mono<UserRestrictionResponse> banUser(UUID userId, BanUserRequest request, ViewerContext viewerContext) {
        UUID adminId = UUID.fromString(viewerContext.getUserId());

        return ModerationAction.USER_BANNED.checkPermission(viewerContext)
                .then(validateUserExists(userId))
                .then(validateNotBanningSelf(userId, adminId))
                .then(validateRelatedContentReport(request.relatedReportId()))
                .flatMap(v -> checkExistingActiveRestriction(userId, RestrictionType.PERMANENT_BAN))
                .flatMap(hasActive -> {
                    if(hasActive){
                        return Mono.error(new ApiException("User is already banned", ErrorCode.VALIDATION_FAILED));
                    }

                    UserRestrictionEntity restriction = UserRestrictionEntity.builder()
                            .userId(userId)
                            .restrictionType(RestrictionType.PERMANENT_BAN)
                            .reason(request.reason())
                            .imposedBy(adminId)
                            .relatedReportId(request.relatedReportId())
                            .startsAt(Instant.now())
                            .expiresAt(null)
                            .isActive(true)
                            .build();

                    // Also deactivate user account
                    return appUserRepository.findAppUserByKeycloakId(userId.toString())
                            .flatMap(appUser -> {
                                appUser.setIsActive(false);
                                return appUserRepository.save(appUser);
                            })
                            .then(userRestrictionRepository.save(restriction))
                            .flatMap(this::mapToRestrictionResponse);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> unbanUser(UUID userId, UnbanRequest request, ViewerContext viewerContext) {
        return ModerationAction.USER_UNBANNED.checkPermission(viewerContext)
                .then(findActiveRestriction(userId, RestrictionType.PERMANENT_BAN))
                .flatMap(restriction -> userRestrictionRepository.liftRestriction(
                        restriction.getId(),
                        UUID.fromString(viewerContext.getUserId()),
                        request.reason()
                ))
                .then(reactivateUserAccount(userId))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Boolean> isUserBanned(UUID userId) {
        return userRestrictionRepository.existsByUserIdRestrictionTypeAndIsActiveTrue(userId, RestrictionType.PERMANENT_BAN);
    }

    @Override
    public Mono<UserRestrictionResponse> getActiveBanForUser(UUID userId, ViewerContext viewerContext){

        UUID requestingUserId = UUID.fromString(viewerContext.getUserId());
        boolean canViewAll = viewerContext.isModeratorOrAdmin();

        if(!canViewAll && !requestingUserId.equals(userId)) {
            return Mono.error(new ApiException("You can only view your own ban status", ErrorCode.FORBIDDEN));
        }

        return findActiveRestriction(userId, RestrictionType.PERMANENT_BAN)
                .flatMap(this::mapToRestrictionResponse);
    }

    // ==================== UTILITIES ====================

    @Override
    public Mono<Integer> getUserActiveWarningCount(UUID userId, ViewerContext viewerContext){

        UUID requestingUserId = UUID.fromString(viewerContext.getUserId());
        boolean canViewAll = viewerContext.isModeratorOrAdmin();

        if(!canViewAll && !requestingUserId.equals(userId)) {
            return Mono.error(new ApiException("You can only view your active warning count", ErrorCode.FORBIDDEN));
        }

        return validateUserExists(userId)
                .then(userWarningRepository.countByUserIdAndIsActiveTrue(userId))
                .map(Long::intValue);
    }

    @Override
    public Mono<Boolean> hasActiveRestriction(UUID userId, RestrictionType restrictionType){
        return userRestrictionRepository.existsByUserIdRestrictionTypeAndIsActiveTrue(userId, restrictionType);
    }

    // ==================== PRIVATE HELPERS ====================

    private Mono<AppUserEntity> validateUserExists(UUID userId) {
        return appUserRepository.findAppUserByKeycloakId(userId.toString())
                .switchIfEmpty(Mono.error(new ApiException("User not found", ErrorCode.RESOURCE_NOT_FOUND)));
    }

    private Mono<Boolean> validateNotWarningSelf(UUID userId, UUID moderatorId) {
        if(userId.equals(moderatorId)) {
            return Mono.error(new ApiException("You cannot warn yourself", ErrorCode.VALIDATION_FAILED));
        }
        return Mono.just(true);
    }

    private Mono<Boolean> validateNotMutingSelf(UUID userId, UUID moderatorId) {
        if(userId.equals(moderatorId)) {
            return Mono.error(new ApiException("You cannot mute yourself", ErrorCode.VALIDATION_FAILED));
        }
        return Mono.just(true);
    }

    private Mono<Boolean> validateNotSuspendingSelf(UUID userId, UUID adminId) {
        if(userId.equals(adminId)) {
            return Mono.error(new ApiException("You cannot suspend yourself", ErrorCode.VALIDATION_FAILED));
        }
        return Mono.just(true);
    }

    private Mono<Boolean> validateNotBanningSelf(UUID userId, UUID adminId) {
        if(userId.equals(adminId)) {
            return Mono.error(new ApiException("You cannot ban yourself", ErrorCode.VALIDATION_FAILED));
        }
        return Mono.just(true);
    }

    private Mono<Boolean> validateRelatedIds(WarnUserRequest request) {
        return Mono.when(
                validateRelatedPost(request.relatedPostId()),
                validateRelatedThread(request.relatedThreadId()),
                validateRelatedContentReport(request.relatedReportId())
        ).thenReturn(true);
    }


    private Mono<Boolean> validateRelatedPost(UUID postId){
        if(postId != null){
            return postRepository.existsById(postId)
                    .flatMap(exists -> {
                        if(!exists){
                            return Mono.error(new ApiException(
                                    "Related post not found: " + postId,
                                    ErrorCode.RESOURCE_NOT_FOUND
                            ));
                        }
                        return Mono.just(true);
                    });
        }
        return Mono.just(true);
    }


    private Mono<Boolean> validateRelatedThread(UUID threadId){
        if(threadId != null){
            return forumThreadRepository.existsById(threadId)
                    .flatMap(exists -> {
                        if(!exists){
                            return Mono.error(new ApiException(
                                    "Related thread not found: " + threadId,
                                    ErrorCode.RESOURCE_NOT_FOUND
                            ));
                        }
                        return Mono.just(true);
                    });
        }
        return Mono.just(true);
    }

    private Mono<Boolean> validateRelatedContentReport(UUID reportId){
        if(reportId != null){
            return contentReportRepository.existsById(reportId)
                    .flatMap(exists -> {
                        if(!exists){
                            return Mono.error(new ApiException(
                                    "Related content report not found: " + reportId,
                                    ErrorCode.RESOURCE_NOT_FOUND
                            ));
                        }
                        return Mono.just(true);
                    });
        }
        return Mono.just(true);
    }

    private Mono<Boolean> checkExistingActiveRestriction(UUID userId, RestrictionType restrictionType) {
        return userRestrictionRepository.existsByUserIdRestrictionTypeAndIsActiveTrue(userId, restrictionType);
    }

    private Mono<UserRestrictionEntity> findActiveRestriction(UUID userId, RestrictionType restrictionType) {
        return userRestrictionRepository.findActiveRestrictionByType(userId, restrictionType)
                .switchIfEmpty(Mono.error(new ApiException(
                        "No active " + restrictionType.name().toLowerCase() + " found for this user",
                        ErrorCode.RESOURCE_NOT_FOUND
                )));
    }

    private Mono<Void> reactivateUserAccount(UUID userId){
        return appUserRepository.findAppUserByKeycloakId(userId.toString())
                .flatMap(appUser -> {
                    appUser.setIsActive(true);
                    return appUserRepository.save(appUser);
                })
                .then();
    }

    private Mono<UserWarningResponse> mapToWarningResponse(UserWarningEntity warning) {

        return getModeratorDisplayName(warning.getWarnedBy())
                .map(displayName -> UserWarningResponse.builder()
                        .id(warning.getId())
                        .userId(warning.getUserId())
                        .warnedBy(warning.getWarnedBy())
                        .warnedByDisplayName(displayName)
                        .warningType(warning.getWarningType())
                        .warningText(warning.getWarningText())
                        .relatedPostId(warning.getRelatedPostId())
                        .relatedThreadId(warning.getRelatedThreadId())
                        .relatedReportId(warning.getRelatedReportId())
                        .warnedAt(warning.getWarnedAt())
                        .acknowledgedAt(warning.getAcknowledgedAt())
                        .expiresAt(warning.getExpiresAt())
                        .isActive(warning.isActive())
                        .build());

    }

    private Mono<UserRestrictionResponse> mapToRestrictionResponse(UserRestrictionEntity restriction) {

        return getModeratorDisplayName(restriction.getImposedBy())
                .map(displayName -> UserRestrictionResponse.builder()
                        .id(restriction.getId())
                        .userId(restriction.getUserId())
                        .restrictionType(restriction.getRestrictionType())
                        .reason(restriction.getReason())
                        .imposedBy(restriction.getImposedBy())
                        .imposedByDisplayName(displayName)
                        .relatedReportId(restriction.getRelatedReportId())
                        .restrictedCategoryId(restriction.getRestrictedCategoryId())
                        .startsAt(restriction.getStartsAt())
                        .expiresAt(restriction.getExpiresAt())
                        .isActive(restriction.isActive())
                        .liftedAt(restriction.getLiftedAt())
                        .liftedBy(restriction.getLiftedBy())
                        .liftReason(restriction.getLiftReason())
                        .build());
    }

    private Mono<String> getModeratorDisplayName(UUID userId) {
        if(userId == null){
            return Mono.just("System");
        }

        return appUserRepository.findAppUserByKeycloakId(userId.toString())
                .map(AppUserEntity::getPublicIdentifier)
                .defaultIfEmpty("Unknown");
    }

}
