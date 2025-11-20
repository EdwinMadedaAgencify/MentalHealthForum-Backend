package com.mentalhealthforum.mentalhealthforum_backend.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // --- General Validation/API Errors ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more input fields failed validation."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The request method is not supported for this endpoint."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The requested media type is not supported for this endpoint."),


    // --- Authentication/AppUser Management Errors ---
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "An account already exists with this username or email."),
    USER_DOES_NOT_EXIST(HttpStatus.NOT_FOUND, "The requested user account was not found."),

    // --- Keycloak Sync Errors ---
    KEYCLOAK_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "An error occurred while syncing user data."),

    // --- Password/Policy Errors ---
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "Password does not meet required complexity standards."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "Password and Confirmation do not match."),

    // --- Pagination Error ---
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "Invalid pagination parameters. Page and size must be valid and positive."),

    // Authentication Failure (401)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication failed. Invalid or missing credentials."),
    AUTHENTICATION_SERVICE_ERROR(HttpStatus.UNAUTHORIZED, "Communication with the Service failed during critical operation."),

    // Authorization Failure (403)
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied. You do not have sufficient permissions for this resource."),

    // --- Action Required Error ---
    USER_ACTION_REQUIRED(HttpStatus.FORBIDDEN, "AppUser authentication succeeded but requires further action."),

    // --- Generic Fallback ---
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred.");


    private final HttpStatus httpStatus;
    private final String description;

    ErrorCode(HttpStatus httpStatus, String description) {
        this.httpStatus = httpStatus;
        this.description = description;
    }

}
