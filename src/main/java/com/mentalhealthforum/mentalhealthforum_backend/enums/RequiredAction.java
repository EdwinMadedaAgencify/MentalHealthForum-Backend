package com.mentalhealthforum.mentalhealthforum_backend.enums;

import lombok.Getter;

import java.util.Optional;

@Getter
public enum RequiredAction {

    VERIFY_EMAIL("Verify Email", "Please check your email and click the verification link."),
    UPDATE_PASSWORD("Update Password", "You must update your temporary password before logging in."),
    UPDATE_PROFILE("Update Profile", "Please complete your profile information."),
    CONFIGURE_TOTP("Configure OTP", "You need to set up 2-factor authentication.");

    private final String displayName; // Human-readable name
    private final String userMessage;

    RequiredAction(String displayName, String userMessage) {
        this.displayName = displayName;
        this.userMessage = userMessage;
    }

    public static Optional<RequiredAction> fromKeycloak(String value){
        try{
            return Optional.of(RequiredAction.valueOf(value));
        }catch(IllegalArgumentException ex){
            return Optional.empty();
        }
    }
}
