package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class UserNotReadyException extends ApiException{
    public UserNotReadyException(String message){
        super(message, ErrorCode.USER_NOT_READY);
    }
}
