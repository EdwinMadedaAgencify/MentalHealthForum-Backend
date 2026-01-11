package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class InvalidTokenException extends ApiException{
    public InvalidTokenException(){
        super(ErrorCode.INVALID_VERIFICATION_TOKEN);
    }

    public InvalidTokenException(String message){
        super(message, ErrorCode.INVALID_VERIFICATION_TOKEN);
    }
}
