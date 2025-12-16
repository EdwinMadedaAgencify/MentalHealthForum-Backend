package com.mentalhealthforum.mentalhealthforum_backend.validation.group;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AssignableGroupValidator.class)
@NotNull(message = "Group assignment is required.")
public @interface ValidAssignableGroup {
    String message() default "Group must be assignable (leaf group with role grants)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
