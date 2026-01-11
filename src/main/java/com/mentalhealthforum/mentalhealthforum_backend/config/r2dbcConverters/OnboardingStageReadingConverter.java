package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;

public class OnboardingStageReadingConverter extends AbstractPostgresEnumReadingConverter<OnboardingStage>{
    public OnboardingStageReadingConverter() {
        super(OnboardingStage.class);
    }
}