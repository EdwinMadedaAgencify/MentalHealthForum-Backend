package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RestrictionType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserModerationService {

    // TODO: Add auto-expiry scheduler for mutes, suspensions, and warnings
    // Location: UserModerationServiceImpl or new Scheduler class
    // - Deactivate expired mutes (is_active = false)
    // - Deactivate expired suspensions
    // - Deactivate expired warnings
    // - Run every minute via @Scheduled

    // TODO: Add "time remaining" to restriction responses
    // Location: getActiveMuteForUser, getActiveSuspendForUser, getActiveBanForUser
    // - Add remainingHours / remainingDays field
    // - Add human-readable "expires in X hours" message

    // TODO: Add "time remaining" to error messages
    // Location: SecurityExceptionHandler, requireNotMuted()
    // - Show "expires in 2 hours" instead of absolute date
    // - More user-friendly for time-sensitive restrictions

    // TODO: Consider moving thread lock expiry to same scheduler
    // Location: ThreadLockExpiryScheduler (currently separate)
    // - Consolidate with other expiry jobs for consistency

    // TODO: ARCHITECTURAL DEBT (Moderation Side Effects Lifecycle):
// 1. CIRCULAR DEPENDENCY: Remove ContentReportRepository from this service entirely.
//    Validation of relatedReportId should be pushed upward to a dedicated
//    ReportWorkflowOrchestrator to prevent a tight cyclical loop between
//    ReportService -> UserModerationService -> ContentReportRepository -> ReportService.
//
// 2. R2DBC UPDATE TRAP: In the banUser() and unbanUser() methods, verify if appUserRepository.save()
//    is throwing a Duplicate Key Exception. Since KeycloakId is a custom string identifier,
//    R2DBC might mistake updates for new INSERTs. If it breaks, migrate the account
//    activation/deactivation to an explicit native @Query("UPDATE...") inside AppUserRepository.

    // ==================== WARNINGS ====================
    Mono<UserWarningResponse> warnUser(UUID userId, WarnUserRequest request, ViewerContext viewerContext);

    Flux<UserWarningResponse> getUserWarnings(UUID userId, ViewerContext viewerContext);

    Mono<Void> acknowledgeWarning(UUID warningId, ViewerContext viewerContext);

    Mono<Void> deactivateWarning(UUID warningId, ViewerContext viewerContext);

    // ==================== MUTES ====================

    Mono<UserRestrictionResponse> muteUser(UUID userId, MuteUserRequest request, ViewerContext viewerContext);

    Mono<Void> unmuteUser(UUID userId, UnMuteUserRequest request, ViewerContext viewerContext);

    Mono<Boolean> isUserMuted(UUID userId);

    // ==================== SUSPENSIONS ====================

    Mono<UserRestrictionResponse> getActiveMuteForUser(UUID userId, ViewerContext viewerContext);

    Mono<Void> requireNotMuted(UUID userId, String actionDescription);

    Mono<UserRestrictionResponse> suspendUser(UUID userId, SuspendUserRequest request, ViewerContext viewerContext);

    Mono<Void> unsuspendUser(UUID userId, UnSuspendRequest reason, ViewerContext viewerContext);

    Mono<Boolean> isUserSuspended(UUID userId);

    // ==================== BANS ====================

    Mono<UserRestrictionResponse> getActiveBanForUser(UUID userId, ViewerContext viewerContext);

    Mono<UserRestrictionResponse> getActiveSuspendForUser(UUID userId, ViewerContext viewerContext);

    Mono<UserRestrictionResponse> banUser(UUID userId, BanUserRequest request, ViewerContext viewerContext);

    Mono<Void> unbanUser(UUID userId, UnbanRequest reason, ViewerContext viewerContext);

    Mono<Boolean> isUserBanned(UUID userId);

    // ==================== UTILITIES ====================

    Mono<Integer> getUserActiveWarningCount(UUID userId, ViewerContext viewerContext);

    Mono<Boolean> hasActiveRestriction(UUID userId, RestrictionType restrictionType);
}
