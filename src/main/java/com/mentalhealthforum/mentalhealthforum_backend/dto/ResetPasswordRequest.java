package com.mentalhealthforum.mentalhealthforum_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

   @NotBlank(message = "New password is required.")
   @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters.")
   String newPassword,

   @NotBlank(message = "New password confirmation is required.")
   String confirmPassword
) {}
