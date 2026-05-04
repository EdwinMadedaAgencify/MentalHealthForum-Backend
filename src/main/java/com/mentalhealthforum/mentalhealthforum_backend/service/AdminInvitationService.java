package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.user.KeycloakUserDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.adminUser.PendingAdminInviteDto;
import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import com.mentalhealthforum.mentalhealthforum_backend.model.AdminInvitationEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;


public interface AdminInvitationService {
    Mono<AdminInvitationEntity> createInvitation(KeycloakUserDto keycloakUserDto, String invitedById);

    Mono<AdminInvitationEntity> updateInvitation(KeycloakUserDto keycloakUserDto);

    Mono<Void> processVerificationSuccess(String userId);

    Mono<Void> processPasswordResetSuccess(String userId);

    Mono<Void> updateOnboardingStage(String userId, OnboardingStage onboardingStage);


    Mono<PaginatedResponse<PendingAdminInviteDto>> getPendingInvites(
            int page,
            int size,
            String[] groups,
            UUID invitedByUserId,
            String sortBy,
            String sortDirection,
            String search,
            OnboardingStage onboardingStage);

    Mono<Void> completeInvitation(UUID keycloakId);
}
