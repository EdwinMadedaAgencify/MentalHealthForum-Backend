package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class InvalidPasswordException extends ApiException {
    public InvalidPasswordException(String message){
        super(message, ErrorCode.PASSWORD_POLICY_VIOLATION);
    }

    public InvalidPasswordException(String message, Throwable cause){
        super(message, ErrorCode.PASSWORD_POLICY_VIOLATION);
    }
}
