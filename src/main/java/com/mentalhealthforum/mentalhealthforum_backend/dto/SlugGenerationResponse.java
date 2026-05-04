package com.mentalhealthforum.mentalhealthforum_backend.dto;

public record SlugGenerationResponse(
        String slug,
        boolean available ) {}
