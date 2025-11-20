package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.LoginRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.AuthService;
import com.mentalhealthforum.mentalhealthforum_backend.utils.SecureCookieUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecureCookieUtils cookieUtils;

    public AuthController(AuthService authService, SecureCookieUtils cookieUtils) {
        this.authService = authService;
        this.cookieUtils = cookieUtils;
    }

    // -------------------------------------------------------------------------
    // MANUAL AUTHENTICATION AND TOKEN LIFECYCLE (Already Reactive)
    // -------------------------------------------------------------------------

    /**
     * Handles manual user login (ROPC Grant). Returns Access and Refresh Tokens.
     * The service returns Mono<JwtResponse>, which is mapped to a 200 OK Response.
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<StandardSuccessResponse<Object>>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            ServerHttpResponse response
    ) {
        return authService.authenticate(loginRequest)
                .map(jwt -> {
                    // Uses SecureCookieUtils to set the tokens, encoding the refresh token value
                    cookieUtils.setTokenCookies(
                            response,
                            jwt.accessToken(),
                            jwt.refreshToken(),
                            jwt.expiresIn(),
                            jwt.refreshExpiresIn());

                    return ResponseEntity.ok(new StandardSuccessResponse<>("Login successful."));
                });
    }

    /**
     * Exchanges an expired refresh token for a new access and refresh token pair.
     * The service returns Mono<JwtResponse>, which is mapped to a 200 OK Response.
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<StandardSuccessResponse<Object>>> refresh(
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        // Use SecureCookieUtils to extract and decode the refresh token from the cookie
        return cookieUtils.getDecodedRefreshToken(request)
                .flatMap(authService::refreshTokens)
                .map(jwt ->{
                    // Use SecureCookieUtils to set the new tokens
                    cookieUtils.setTokenCookies(
                            response,
                            jwt.accessToken(),
                            jwt.refreshToken(),
                            jwt.expiresIn(),
                            jwt.refreshExpiresIn());

                    return ResponseEntity.ok(new StandardSuccessResponse<>("Refresh successful."));
                });
    }
    /**
     * AppUser logout (Token Revocation).
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> logoutUser(
            ServerHttpRequest request,
            ServerHttpResponse response
    ){
        // Use SecureCookieUtils to extract and decode the refresh token from the cookie
        // If the cookie is missing, we still clear the cookies and report success.
        Mono<String> refreshTokenMono = cookieUtils.getDecodedRefreshToken(request)
                .onErrorResume(IllegalStateException.class, e -> Mono.empty()); // Return empty Mono if cookie is missing

        return refreshTokenMono
                .flatMap(authService::logout)
                .doOnTerminate(() -> cookieUtils.clearTokenCookies(response)) // Clear cookies regardless of logout success/failure
                .thenReturn(ResponseEntity.ok(
                        new StandardSuccessResponse<>(
                                "Logout successful."
                        )
                ));
    }
}
