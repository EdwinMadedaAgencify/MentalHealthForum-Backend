package com.mentalhealthforum.mentalhealthforum_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardErrorResponse;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    // ObjectMapper is injected by Spring Boot for reliable JSON serialization
    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // --- Handles 401 Unauthorized (Authentication Failure) ---
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        logger.warn("401 Unauthorized Access Attempt: {}", authException.getMessage());

        writeErrorResponse(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                "Authentication failed. Invalid or missing credentials.",
                request.getRequestURI()
        );
    }

    // --- Handles 403 Forbidden (Authorization Failure) ---
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {

        logger.warn("403 Forbidden Access Attempt: {}", accessDeniedException.getMessage());

        writeErrorResponse(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.FORBIDDEN,
                "Forbidden: Insufficient permissions for this resource.",
                request.getRequestURI()
        );
    }

    /**
     * Sets headers, creates the custom DTO, writes JSON, and flushes the stream.
     */
    private void writeErrorResponse(
            HttpServletResponse response,
            int httpStatus,
            ErrorCode errorCode,
            String message,
            String path
    ) throws IOException {

        // 1. Set Status and Headers
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // 2. Create the DTO (Java Record, using the compact constructor)
        StandardErrorResponse errorResponse = new StandardErrorResponse(
                message,
                errorCode,
                path,
                null // Passing null for the details list
        );

        // 3. WRITE THE JSON AND FLUSH IMMEDIATELY
        try {
            // Write the JSON using the injected ObjectMapper
            objectMapper.writeValue(response.getWriter(), errorResponse);

            // CRITICAL: Force the output buffer to be written immediately to the client.
            response.getWriter().flush();
            response.flushBuffer();
        } catch (IOException e) {
            logger.error("Failed to write custom error response for status {}: {}", httpStatus, e.getMessage());
            throw e;
        }
    }
}