package com.mentalhealthforum.mentalhealthforum_backend.enums;

import lombok.Getter;

@Getter
public enum InternalRole {
    ONBOARDING("ONBOARDING", "Authenticated but restricted to onboarding only");

    private final String roleName;
    private final String description;


    InternalRole(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }
}
