package com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;

public class ProfileVisibilityReadingConverter extends AbstractPostgresEnumReadingConverter<ProfileVisibility>{
    public ProfileVisibilityReadingConverter() {
        super(ProfileVisibility.class);
    }
}