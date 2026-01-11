package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.validation.ValidEmail;
import com.mentalhealthforum.mentalhealthforum_backend.validation.password.PasswordMatching;
import com.mentalhealthforum.mentalhealthforum_backend.validation.password.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordMatching(passwordField = "newPassword")
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required.")
        @ValidEmail
        String email,

        @NotBlank(message = "Otp code is required.")
        String otpCode,

        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters.")
        @StrongPassword
        String newPassword,

        @NotBlank(message = "New password confirmation is required.")
        String confirmPassword
) {}
