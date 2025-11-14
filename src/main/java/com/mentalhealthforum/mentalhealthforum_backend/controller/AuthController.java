package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.JwtResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.LoginRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.RefreshRequest;
import com.mentalhealthforum.mentalhealthforum_backend.service.AuthService;
import jakarta.validation.Valid;
import org.keycloak.representations.RefreshToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // -------------------------------------------------------------------------
    // MANUAL AUTHENTICATION AND TOKEN LIFECYCLE (Already Reactive)
    // -------------------------------------------------------------------------

    /**
     * Handles manual user login (ROPC Grant). Returns Access and Refresh Tokens.
     * The service returns Mono<JwtResponse>, which is mapped to a 200 OK Response.
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<JwtResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.authenticate(loginRequest)
                .map(ResponseEntity::ok);
    }

    /**
     * Exchanges an expired refresh token for a new access and refresh token pair.
     * The service returns Mono<JwtResponse>, which is mapped to a 200 OK Response.
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<JwtResponse>> refresh(@Valid @RequestBody RefreshRequest refreshRequest) {
        return authService.refreshTokens(refreshRequest.refreshToken())
                .map(ResponseEntity::ok);
    }

    /**
     * User logout (Token Revocation).
     * The client must send the refresh token in the body for revocation.
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<String>> logoutUser(@RequestBody RefreshRequest refreshRequest){
        return authService.logout(refreshRequest.refreshToken())
                .then(Mono.just(new ResponseEntity<>("Logged out successfully. Token revocation attempted.", HttpStatus.OK)));
    }
}
