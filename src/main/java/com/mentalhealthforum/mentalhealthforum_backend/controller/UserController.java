package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPasswordException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.PasswordMismatchException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserDoesNotExistException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserExistsException;
import com.mentalhealthforum.mentalhealthforum_backend.service.AuthService;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserService;
import jakarta.validation.Valid;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    // -------------------------------------------------------------------------
    // MANUAL AUTHENTICATION AND TOKEN LIFECYCLE
    // -------------------------------------------------------------------------

    /**
     * Handles manual user login (ROPC Grant). Returns Access and Refresh Tokens.
     * Uses Mono.block() for compatibility with Spring MVC and Springdoc.
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest loginRequest){
        try{
            // Block until the non-blocking service call completes
            JwtResponse jwtResponse = authService.authenticate(loginRequest).block();
            return ResponseEntity.ok(jwtResponse);
        } catch (WebClientResponseException e){
            // Catches authentication failures from Keycloak
            System.err.println("Keycloak authentication failed: " + e.getResponseBodyAsString());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during login: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Exchanges an expired refresh token for a new access and refresh token pair.
     * Uses Mono.block() for compatibility with Spring MVC and Springdoc.
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@Valid @RequestBody RefreshRequest refreshRequest){
        try{
            // Block until the non-blocking service call completes
            JwtResponse jwtResponse = authService.refreshTokens(refreshRequest.refreshToken()).block();
            return ResponseEntity.ok(jwtResponse);
        } catch (WebClientResponseException e){
            // Catches token refresh failures (e.g., expired refresh token)
            System.err.println("Keycloak token refresh failed: " + e.getResponseBodyAsString());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e){
            // Handles other unexpected blocking exceptions
            System.err.println("An unexpected error occured during refresh: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // -------------------------------------------------------------------------
    // USER MANAGEMENT (Existing Endpoints)
    // -------------------------------------------------------------------------
    @PostMapping("/register")
    public ResponseEntity<StandardSuccessResponse<String>> registerUser(
            @Valid @RequestBody RegisterUserRequest registerUserRequest) throws UserExistsException, InvalidPasswordException, PasswordMismatchException {

        String userId = userService.registerUser(registerUserRequest);

        String message = "User registered successfully. Activation required.";
        StandardSuccessResponse<String> response = new StandardSuccessResponse<>(message, userId);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(userId)
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<StandardSuccessResponse<UserRepresentation>> getUser(@PathVariable String userId) throws UserDoesNotExistException{
        UserRepresentation user = userService.getUser(userId);
        String message = "User details retrieved successfully";

        StandardSuccessResponse<UserRepresentation> response = new StandardSuccessResponse<>(message, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<StandardSuccessResponse<PaginatedResponse<UserRepresentation>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size){

        PaginatedResponse<UserRepresentation> paginatedUsers = userService.getAllUsers(page, size);

        String message = "Paginated user records retrieved successfully.";

        StandardSuccessResponse<PaginatedResponse<UserRepresentation>> response = new StandardSuccessResponse<>(message, paginatedUsers);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<StandardSuccessResponse<UserRepresentation>> updateUserProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest) throws UserExistsException, UserDoesNotExistException {

        UserRepresentation updatedUser = userService.updateUserProfile(userId, updateUserProfileRequest);

        String message = "User updated successfully.";

        StandardSuccessResponse<UserRepresentation> response = new StandardSuccessResponse<>(message, updatedUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<StandardSuccessResponse<Void>> resetPassword(
            @RequestParam String userId,
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest) throws PasswordMismatchException,  InvalidPasswordException, UserDoesNotExistException {

        userService.resetPassword(userId, resetPasswordRequest);

        String message = "User reset password successfully.";

        StandardSuccessResponse<Void> response = new StandardSuccessResponse<>(message);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) throws UserDoesNotExistException {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
