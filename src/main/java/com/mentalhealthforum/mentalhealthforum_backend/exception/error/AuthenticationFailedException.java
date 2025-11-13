package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(message, ErrorCode.UNAUTHORIZED);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, ErrorCode.UNAUTHORIZED, cause);
    }
}