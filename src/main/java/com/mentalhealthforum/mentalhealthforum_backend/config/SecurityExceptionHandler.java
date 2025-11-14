package com.mentalhealthforum.mentalhealthforum_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardErrorResponse;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Reactive exception handler for 401 (Authentication) and 403 (Authorization) errors.
 * Implements reactive interfaces ServerAuthenticationEntryPoint and ServerAccessDeniedHandler.
 */
@Component
public class SecurityExceptionHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // --- Handles 401 Unauthorized (Authentication Failure) ---
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        logger.warn("401 Unauthorized Access Attempt: {}", ex.getMessage());
        return writeErrorResponse(
                exchange,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                "Authentication failed. Invalid or missing credentials."
        );
    }

    // --- Handles 403 Forbidden (Authorization Failure) ---
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        logger.warn("403 Forbidden Access Attempt: {}", ex.getMessage());
        return writeErrorResponse(
                exchange,
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN,
                "Forbidden: Insufficient permissions for this resource."
        );
    }

    /**
     * Sets headers, creates the custom DTO, writes JSON to the response body, and completes the Mono.
     */
    private Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            HttpStatus httpStatus,
            ErrorCode errorCode,
            String message
    ) {
        // 1. Set Status and Headers
        exchange.getResponse().setStatusCode(httpStatus);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setAccessControlAllowOrigin("*"); // Important for CORS errors

        // 2. Create the DTO
        StandardErrorResponse errorResponse = new StandardErrorResponse(
                message,
                errorCode,
                exchange.getRequest().getPath().toString(),
                null
        );

        // 3. Serialize DTO to JSON and write to response body
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            logger.error("Failed to write custom error response for status {}: {}", httpStatus, e.getMessage());
            return Mono.error(e);
        }
    }
}