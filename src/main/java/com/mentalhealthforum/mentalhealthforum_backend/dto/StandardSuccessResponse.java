package com.mentalhealthforum.mentalhealthforum_backend.dto;

import java.time.Instant;
import java.util.Optional;

public record StandardSuccessResponse<T>(
        boolean success,
        String message,
        Optional<T> data,
        Instant timestamp
) {
    /**
     * Compact constructor to initialize immutable fields automatically.
     * This is the ideal place to set fixed values like 'success = true' and 'timestamp = Instant.now()'.
     */
   public StandardSuccessResponse{
   }
    /**
     * Constructor for success responses that include data.
     * Only message and data are required, simplifying usage.
     */
   public StandardSuccessResponse(String message, T data){
       this(true, message, Optional.ofNullable(data), Instant.now());
   }

    /**
     * Constructor for success responses that do not need a data payload (e.g., DELETE, simple update).
     */
    public StandardSuccessResponse(String message){
        this(true, message, Optional.empty(), Instant.now());
    }
}
