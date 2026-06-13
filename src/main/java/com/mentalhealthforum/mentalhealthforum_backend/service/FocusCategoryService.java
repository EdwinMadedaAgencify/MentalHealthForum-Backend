package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.FocusCategoryResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FocusCategoryService {
    Mono<FocusCategoryResponse> addFocusCategory(UUID categoryId, ViewerContext viewerContext);

    Mono<Void> removeFocusCategory(UUID categoryId, ViewerContext viewerContext);

    Mono<Boolean> isCategoryFocused(UUID categoryId, ViewerContext viewerContext);

    Mono<PaginatedResponse<FocusCategoryResponse>> getFocusCategories(
            int page,
            int size,
            String search,
            String sortBy,
            String sortDirection,
            Boolean notificationEnabled,
            ViewerContext viewerContext
    );

    Mono<Long> getFocusCategoriesCount(ViewerContext viewerContext);
}
