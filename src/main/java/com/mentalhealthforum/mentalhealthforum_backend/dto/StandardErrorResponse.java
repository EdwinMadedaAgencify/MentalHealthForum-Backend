package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

import java.time.Instant;
import java.util.List;

// Used as the unified response body for all errors
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StandardErrorResponse(
        boolean success, // automatically calculated
        String message,
        ErrorCode errorCode,
        int statusCode,
        String path,
        List<ErrorDetail> details,
        Instant timestamp  // automatically calculated

) {
    // Compact constructor used by handlers.
    // It accepts only the variables that change per error.
    public StandardErrorResponse(
            String message,
            ErrorCode errorCode,
            String path,
            List<ErrorDetail> details
    ) {
        // Calls the full primary constructor with fixed values
        this(
                false, // Fixed value for success
                message,
                errorCode,
                errorCode.getHttpStatus().value(), // Status code derived from enum
                path,
                details,
                Instant.now() // Fixed value for timestamp
        );
    }
}