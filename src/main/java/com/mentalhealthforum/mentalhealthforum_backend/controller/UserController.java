package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.AuthService;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest; // Import the MVC-native request
import jakarta.validation.Valid;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder; // Use standard UriComponentsBuilder
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
    }

    // -------------------------------------------------------------------------
    // USER MANAGEMENT (Refactored to Reactive)
    // -------------------------------------------------------------------------

    @PostMapping("/register")
    public Mono<ResponseEntity<StandardSuccessResponse<String>>> registerUser(
            @Valid @RequestBody RegisterUserRequest registerUserRequest,
            // Inject the MVC-native request object
            HttpServletRequest request) {

        // 1. Capture the base URI components immediately (outside the reactive chain)
        // This relies on the current thread having access to the HttpServletRequest data.
        final URI currentUri = UriComponentsBuilder
                .fromUriString(request.getRequestURL()
                .toString())
                .build()
                .toUri();

        // userService.registerUser returns Mono<String> (the userId)
        return userService.registerUser(registerUserRequest)
                .map(userId -> {
                    // Map the userId into a 201 Created Response
                    String message = "User registered successfully. Activation required.";
                    StandardSuccessResponse<String> response = new StandardSuccessResponse<>(message, userId);

                    // FIX: Use UriComponentsBuilder with the request's URI (reactive safe)
                    URI location = UriComponentsBuilder.fromUri(currentUri)
                            .path("/{id}") // Appends "/{id}"
                            .buildAndExpand(userId) // Inserts the newly generated userId
                            .toUri(); // Finalizes the URI object

                    return ResponseEntity.created(location).body(response);
                });
    }

    @GetMapping("/{userId}")
    public Mono<ResponseEntity<StandardSuccessResponse<UserRepresentation>>> getUser(@PathVariable String userId) {

        // userService.getUser returns Mono<UserRepresentation>
        return userService.getUser(userId)
                .map(user -> {
                    String message = "User details retrieved successfully";
                    StandardSuccessResponse<UserRepresentation> response = new StandardSuccessResponse<>(message, user);
                    return ResponseEntity.ok(response);
                });
    }

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<UserRepresentation>>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size){

        // userService.getAllUsers returns Mono<PaginatedResponse<UserRepresentation>>
        return userService.getAllUsers(page, size)
                .map(paginatedUsers -> {
                    String message = "Paginated user records retrieved successfully.";
                    StandardSuccessResponse<PaginatedResponse<UserRepresentation>> response = new StandardSuccessResponse<>(message, paginatedUsers);
                    return ResponseEntity.ok(response);
                });
    }

    @PatchMapping("/{userId}")
    public Mono<ResponseEntity<StandardSuccessResponse<UserRepresentation>>> updateUserProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest) {

        // userService.updateUserProfile returns Mono<UserRepresentation> (the updated user)
        return userService.updateUserProfile(userId, updateUserProfileRequest)
                .map(updatedUser -> {
                    String message = "User updated successfully.";
                    StandardSuccessResponse<UserRepresentation> response = new StandardSuccessResponse<>(message, updatedUser);
                    return ResponseEntity.ok(response);
                });
    }

    @PostMapping("/reset-password")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> resetPassword(
            @RequestParam String userId,
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {

        // userService.resetPassword returns Mono<Void>. We use then() to wait for completion.
        return userService.resetPassword(userId, resetPasswordRequest)
                .then(Mono.fromCallable(() -> {
                    String message = "User reset password successfully.";
                    StandardSuccessResponse<Void> response = new StandardSuccessResponse<>(message);
                    return ResponseEntity.ok(response);
                }));
    }

    @DeleteMapping("/{userId}")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable String userId) {
        // userService.deleteUser returns Mono<Void>. We use thenReturn() to wait for completion
        // and then emit the 204 No Content response.
        return userService.deleteUser(userId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}