package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class UserExistsException extends ApiException{
    public UserExistsException(String message){
        super(message, ErrorCode.USER_ALREADY_EXISTS);
    }
}
