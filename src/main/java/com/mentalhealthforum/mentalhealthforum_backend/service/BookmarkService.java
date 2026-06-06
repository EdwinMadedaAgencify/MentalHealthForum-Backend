package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BookmarkService {
    Mono<BookmarkResponse> addBookmark(BookmarkRequest request, ViewerContext viewerContext);

    Mono<Void> removeBookmark(UUID threadId, ViewerContext viewerContext);

    Mono<PaginatedResponse<BookmarkResponse>> getMyBookmarks(
            int page,
            int size,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    );

    Mono<Boolean> isBookmarked(UUID threadId, ViewerContext viewerContext);

    Mono<Long> getBookmarkCountByUserId(ViewerContext viewerContext);


    Mono<Long> getBookmarkCountForThread(UUID threadId);
}
