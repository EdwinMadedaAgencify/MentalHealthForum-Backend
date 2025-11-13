package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class PasswordMismatchException extends ApiException {
    public PasswordMismatchException(String message){
        super(message, ErrorCode.PASSWORD_CONFIRM_MISMATCH);
    }
}
