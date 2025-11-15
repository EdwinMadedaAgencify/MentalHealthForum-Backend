package com.mentalhealthforum.mentalhealthforum_backend.utils;

import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Utility component for setting, retrieving, and clearing HTTP-only, secure cookies.
 * It implements a basic level of obfuscation (Base64 encoding) on the sensitive
 * REFRESH_TOKEN value before writing it to the cookie.
 */
@Component
public class SecureCookieUtils {

    // -- Cookie Names ---
    private static final String ACCESS_TOKEN_NAME = "ACCESS_TOKEN";
    private static final String REFRESH_TOKEN_NAME = "REFRESH_TOKEN";

    /**
     * Sets both ACCESS_TOKEN (plain) and REFRESH_TOKEN (encoded) cookies on the response.
     */
    public void setTokenCookies(
            ServerHttpResponse response,
            String accessToken,
            String refreshToken,
            long accessTokenExpirySec,
            long refreshTokenExpirySec
    ){
        final boolean isSecure = false;

        // 1. ACCESS_TOKEN (Short-lived, used in Auth header usually, kept plain in cookie)
        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_TOKEN_NAME, accessToken)
                .httpOnly(true)
                .secure(isSecure) // Ensure it's only sent over HTTPS
                .path("/")
                .maxAge(accessTokenExpirySec)
                .sameSite("Strict")
                .build();

        // 2. REFRESH_TOKEN (Long-lived, highly sensitive, must be encoded/encrypted)
        // Here we use Base64 encoding as a placeholder for a more robust encryption scheme (e.g., JWE/AES).
        String encodedRefreshToken = encodeValue(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_TOKEN_NAME, encodedRefreshToken)
                .httpOnly(true)
                .secure(isSecure) // Ensure it's only sent over HTTPS
                .path("/")
                .maxAge(refreshTokenExpirySec)
                .sameSite("Strict")
                .build();


        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);
    }

    /**
     * Retrieves and decodes the refresh token value from the incoming request cookies.
     * @return Mono<String> containing the decoded refresh token value.
     */
    public Mono<String> getDecodedRefreshToken(ServerHttpRequest request){
        MultiValueMap<String, org.springframework.http.HttpCookie> cookies = request.getCookies();

        Optional<org.springframework.http.HttpCookie> cookieOpt = Optional.ofNullable(cookies.getFirst(REFRESH_TOKEN_NAME));

         return Mono.justOrEmpty(cookieOpt)
         .map(org.springframework.http.HttpCookie::getValue)
         .switchIfEmpty(Mono.error(new IllegalStateException("Refresh token cookie missing.")))
         .map(this::decodeValue);
    }

    /**
     * Clears both access and refresh token cookies by setting empty values and immediate expiry.
     */
    public void clearTokenCookies(
            ServerHttpResponse response
    ){
        // 1. ACCESS_TOKEN (Short-lived, used in Auth header usually, kept plain in cookie)
        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_TOKEN_NAME, "")
                .httpOnly(true)
                .secure(true) // Ensure it's only sent over HTTPS
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_TOKEN_NAME, "")
                .httpOnly(true)
                .secure(true) // Ensure it's only sent over HTTPS
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);
    }

    // --- Internal Encoding/Decoding Logic ---

    /**
     * Encodes the refresh token value using Base64.
     * NOTE: In a production environment, this should be replaced with strong,
     * authenticated encryption (e.g., AES-256 GCM or JWE).
     */
    private String encodeValue(String value){
        // Apply Base64 encoding to make the token value less readable in network traffic/logs.
        String base64value = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        // URL-encode the result to ensure it's cookie-safe.
        return URLEncoder.encode(base64value, StandardCharsets.UTF_8);
    }

    /**
     * Decodes the value read from the refresh token cookie.
     */
    private String decodeValue(String encodedValue){
        try{
            // 1. URL-decode the cookie value
            String urlDecoded = URLDecoder.decode(encodedValue, StandardCharsets.UTF_8);
            // 2. Base64-decode the token content
            byte[] decodedBytes = Base64.getDecoder().decode(urlDecoded);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e){
            throw new IllegalArgumentException("Failed to decode refresh token cookie value.", e);
        }
    }

}
