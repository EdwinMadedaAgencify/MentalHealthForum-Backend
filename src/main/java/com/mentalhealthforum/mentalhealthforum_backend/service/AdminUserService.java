package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.AdminCreateUserRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.AdminCreateUserResponse;
import reactor.core.publisher.Mono;

public interface AdminUserService {
    Mono<AdminCreateUserResponse> createUserAsAdmin(AdminCreateUserRequest request);
}
