package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.AdminUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/users/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final AdminUserService adminUserService;
    private final AppUserService appUserService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public AdminUserController(AdminUserService adminUserService, AppUserService appUserService, JwtClaimsExtractor jwtClaimsExtractor) {
        this.adminUserService = adminUserService;
        this.appUserService = appUserService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
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
    @PostMapping("/create")
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

    @PostMapping("/{userId}")
    public Mono<ResponseEntity<StandardSuccessResponse<UserResponse>>> updateUserAsAdmin(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserRequest adminUpdateUserRequest){

        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);

        return adminUserService.updateUserAsAdmin(userId, adminUpdateUserRequest)
                // ADD DELAY to allow Keycloak to propagate role changes
                .delayElement(Duration.ofMillis(500))
                .flatMap(keycloakUserDto -> appUserService.syncUserViaAdminClient(keycloakUserDto, viewerContext))
                .then(appUserService.getAppUserWithContext(userId, viewerContext))
                .map(user -> {
                    String message = "User details updated successfully";
                    StandardSuccessResponse<UserResponse> response = new StandardSuccessResponse<>(message, user);
                    return ResponseEntity.ok(response);
                });
    }
}
