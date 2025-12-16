package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.AdminCreateUserRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.AdminCreateUserResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.AdminUserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * ADMIN-ONLY: Creates a new user with admin privileges.

     * Different from self-registration:
     * - Auto-generates password (returns in response)
     * - Sets pending actions for onboarding
     * - Allows explicit group assignment
     * - Returns invitation details for manual sending

     * Security: Requires ADMIN role or equivalent privilege.
     *
     * @param jwt Authentication token for authorization check
     * @param request User creation details including group assignment
     * @return Created user response with temporary credentials
     */
    @PostMapping("/admin/create")
    public Mono<ResponseEntity<StandardSuccessResponse<AdminCreateUserResponse>>> createUserAsAdmin(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminCreateUserRequest request){

        log.info("Admin creating user: email = {}, group = {}", request.email(), request.group().getPath());

        return adminUserService.createUserAsAdmin(request)
                .map(response -> {
                    // Build success response
                    String message = "User created successfully. " +
                            (request.sendInvitationEmail() ?
                                    "Invitation email sent." :
                                    "Provide temporary credentials to user manually.");

                    StandardSuccessResponse<AdminCreateUserResponse> successResponse =
                            new StandardSuccessResponse<>(message, response);

                    // Return 201 Created with response body
                    return ResponseEntity.status(HttpStatus.CREATED).body(successResponse);
                });
    }
}
