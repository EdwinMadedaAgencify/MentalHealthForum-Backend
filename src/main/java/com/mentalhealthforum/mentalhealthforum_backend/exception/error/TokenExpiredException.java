package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class TokenExpiredException extends ApiException{
    public TokenExpiredException(){
        super(ErrorCode.EXPIRED_VERIFICATION_TOKEN);
    }
}
