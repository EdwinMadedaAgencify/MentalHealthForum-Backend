package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.dto.adminUser.AdminCreateUserRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.adminUser.AdminCreateUserResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.adminUser.AdminUpdateUserRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.adminUser.ReissueInvitationRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.user.KeycloakUserDto;
import reactor.core.publisher.Mono;

public interface AdminUserService {
    Mono<AdminCreateUserResponse> createUserAsAdmin(AdminCreateUserRequest request, ViewerContext viewerContext);

    Mono<AdminCreateUserResponse> reissueAdminInvitation(String userId, ReissueInvitationRequest request);

    Mono<KeycloakUserDto> updateUserAsAdmin(String userId, AdminUpdateUserRequest request);

    Mono<Void> revokeInvitation(String userId);
}
