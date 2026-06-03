package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.moderationEnhancedActionsAndWorkflows.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RestrictionType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserModerationService {

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
