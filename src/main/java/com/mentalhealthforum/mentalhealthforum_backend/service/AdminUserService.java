package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import reactor.core.publisher.Mono;

public interface AdminUserService {
    Mono<AdminCreateUserResponse> createUserAsAdmin(AdminCreateUserRequest request);
    Mono<KeycloakUserDto> updateUserAsAdmin(String userId, AdminUpdateUserRequest request);
}
