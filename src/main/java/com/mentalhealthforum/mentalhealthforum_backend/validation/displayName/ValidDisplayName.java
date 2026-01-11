package com.mentalhealthforum.mentalhealthforum_backend.validation.displayName;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Size(min = 3, max = 100, message = "Display name must be between 3 and 100 characters.")
@Pattern(
        regexp = "^[\\p{L}0-9 .'-]+$",
        message = "Display name can only contain letters, numbers, spaces, dots, apostrophes, and hyphens")
@Pattern(
        regexp = "^(?![. ])(?!.*[. ]$)(?!.*[. ]{2,}).*$",
        message = "Display name cannot start/end with a dot or space, and cannot have consecutive dots or spaces."
)
public @interface ValidDisplayName {
    String message() default "Invalid display name";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
