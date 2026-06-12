package com.mentalhealthforum.mentalhealthforum_backend.enums;

public enum ConnectionStatus {
    PENDING, // Request sent, waiting for response
    ACCEPTED,  // Mutual connection established
    DECLINED, // Request was quietly ignored (soft rejection)
}
