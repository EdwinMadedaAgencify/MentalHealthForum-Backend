package com.mentalhealthforum.mentalhealthforum_backend.validation.username;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;
import java.util.regex.Pattern;

public class UsernameValidator implements ConstraintValidator<ValidUsername, String> {
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$");

    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin", "administrator", "root", "system", "null",
            "undefined", "moderator", "support", "staff"
    );

    @Override
    public boolean isValid(String  username, ConstraintValidatorContext context) {
        if(username == null || username.trim().isEmpty()) return true;

        if(!USERNAME_PATTERN.matcher(username.trim()).matches()){
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Username can only contain letters, numbers, dots, dashes, and underscores, " +
                            "and cannot start or end with special characters"
            ).addConstraintViolation();
            return false;
        }

        if(RESERVED_USERNAMES.contains(username.trim().toLowerCase())){
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    String.format("Username '%s' is reserved. Please choose a different one.", username)
            ).addConstraintViolation();
            return false;
        }
        return true;
    }
}
