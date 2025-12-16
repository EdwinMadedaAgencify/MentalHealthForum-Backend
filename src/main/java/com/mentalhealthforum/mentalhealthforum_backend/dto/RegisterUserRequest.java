package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.validation.ValidEmail;
import com.mentalhealthforum.mentalhealthforum_backend.validation.firstName.ValidFirstName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.lastName.ValidLastName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.password.PasswordMatching;
import com.mentalhealthforum.mentalhealthforum_backend.validation.password.StrongPassword;
import com.mentalhealthforum.mentalhealthforum_backend.validation.username.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordMatching
public record RegisterUserRequest(
        @NotBlank(message = "Username is required.")
        @ValidUsername
        String username,

        @NotBlank(message = "Email is required.")
        @ValidEmail
        String email,

        @NotBlank(message = "Password' is required.")
        @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters.")
        @StrongPassword
        String password,

        @NotBlank(message = "Password confirmation is required.")
        String confirmPassword,

        @NotBlank(message = "First name is required.")
        @ValidFirstName
        String firstName,

        @NotBlank(message = "Last name is required.")
        @ValidLastName
        String lastName
){}
