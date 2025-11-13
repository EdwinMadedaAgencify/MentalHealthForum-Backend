package com.mentalhealthforum.mentalhealthforum_backend.dto;

public record ErrorDetail(
        String field,
        String message
) {}
