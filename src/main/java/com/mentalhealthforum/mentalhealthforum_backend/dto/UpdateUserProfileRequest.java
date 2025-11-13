package com.mentalhealthforum.mentalhealthforum_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(

        @Email(message = "Email must be a valid email format.")
        @Size(max = 100, message = "Email cannot exceed 100 characters.")
        String email,

        @Size(max = 50, message = "First name cannot exceed 50 characters.")
        String firstName,

        @Size(max = 50, message = "Last name cannot exceed 50 characters.")
        String lastName
) {}
