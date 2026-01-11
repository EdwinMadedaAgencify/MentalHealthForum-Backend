package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AdminUserService {
    Mono<AdminCreateUserResponse> createUserAsAdmin(AdminCreateUserRequest request, ViewerContext viewerContext);

    Mono<AdminCreateUserResponse> reissueAdminInvitation(String userId, ReissueInvitationRequest request);

    Mono<KeycloakUserDto> updateUserAsAdmin(String userId, AdminUpdateUserRequest request);

    Mono<Void> revokeInvitation(String userId);
}
