package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class UsernameGenerationException extends ApiException{
    public UsernameGenerationException(String message) {
        super(message, ErrorCode.USERNAME_GENERATION_FAILED);
    }
}
