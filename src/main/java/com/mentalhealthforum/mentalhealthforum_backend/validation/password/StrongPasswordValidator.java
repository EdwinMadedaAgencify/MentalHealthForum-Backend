package com.mentalhealthforum.mentalhealthforum_backend.validation.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validates password strength using the same regex as Keycloak.
 * This ensures consistency between our validation and Keycloak's policy.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // Same regex as in KeycloakAdminManagerImpl for consistency
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile(PasswordPolicy.PASSWORD_POLICY_REGEX);

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if(password == null || password.trim().isEmpty()){
            // Let @NotBlank handle empty validation
            return true;
        }
        if(!PASSWORD_PATTERN.matcher(password).matches()){
            // Optional: Provide more specific error message
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                PasswordPolicy.MESSAGE
            ).addConstraintViolation();
            return false;
        }
      return true;
    }
}
