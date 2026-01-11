package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class OnboardingRequiredException extends ApiException{
    public OnboardingRequiredException() {
        super(ErrorCode.ONBOARDING_REQUIRED.getDescription(), ErrorCode.ONBOARDING_REQUIRED);
    }
}
