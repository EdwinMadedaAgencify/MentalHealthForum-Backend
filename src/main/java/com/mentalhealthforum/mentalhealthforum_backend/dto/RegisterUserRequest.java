package com.mentalhealthforum.mentalhealthforum_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 20 characters.")
        String username,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid email format.")
        @Size(max = 100, message = "Email cannot exceed 100 characters.")
        String email,

        @NotBlank(message = "Password' is required.")
        @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters.")
        String password,

        @NotBlank(message = "Password confirmation is required.")
        String confirmPassword,

        @NotBlank(message = "First name is required.")
        @Size(max = 50, message = "First name cannot exceed 50 characters.")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Size(max = 50, message = "Last name cannot exceed 50 characters.")
        String lastName
){}
