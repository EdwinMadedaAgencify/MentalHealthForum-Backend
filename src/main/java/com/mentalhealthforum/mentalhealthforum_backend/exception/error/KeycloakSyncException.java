package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

public class KeycloakSyncException extends ApiException{

    public KeycloakSyncException(String message){
        super(message, ErrorCode.KEYCLOAK_SYNC_FAILED);
    }

    public KeycloakSyncException(String message, Throwable cause){
        super(message, ErrorCode.KEYCLOAK_SYNC_FAILED, cause);
    }

    public KeycloakSyncException(String message, ErrorCode errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
