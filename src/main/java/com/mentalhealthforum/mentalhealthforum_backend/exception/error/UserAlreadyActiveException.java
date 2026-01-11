package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class UserAlreadyActiveException extends ApiException{
    public UserAlreadyActiveException() {
        super(ErrorCode.USER_ALREADY_ACTIVE);
    }
}
