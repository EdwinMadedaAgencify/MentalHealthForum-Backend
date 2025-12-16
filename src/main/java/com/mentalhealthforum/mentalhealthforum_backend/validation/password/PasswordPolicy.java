package com.mentalhealthforum.mentalhealthforum_backend.validation.password;

public class PasswordPolicy {
    public static final String PASSWORD_POLICY_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9\\s]).{8,}$";
    public static final String MESSAGE = "Password must contain at least 8 characters, including 1 digit, 1 uppercase letter, 1 lowercase letter, and 1 special character";
}
