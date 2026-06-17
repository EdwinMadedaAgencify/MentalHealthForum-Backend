package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkedThreadRecord;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.model.ThreadBookmarkEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ThreadBookmarkRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.BookmarkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

@Service
public class BookmarkServiceImpl implements BookmarkService {

    private final TransactionalOperator transactionalOperator;
    private final ThreadBookmarkRepository bookmarkRepository;
    private final ThreadRepository threadRepository;
    private final AppUserService appUserService;

    public BookmarkServiceImpl(
            TransactionalOperator transactionalOperator,
            ThreadBookmarkRepository bookmarkRepository,
            ThreadRepository threadRepository,
            AppUserService appUserService) {
        this.transactionalOperator = transactionalOperator;
        this.bookmarkRepository = bookmarkRepository;
        this.threadRepository = threadRepository;
        this.appUserService = appUserService;
    }

    @Override
    public Mono<BookmarkResponse> addBookmark(BookmarkRequest request, ViewerContext viewerContext){

        UUID userId = UUID.fromString(viewerContext.getUserId());
        UUID threadId = request.threadId();

        return validateThreadExists(threadId)
                .then(checkNotAlreadyBookmarked(userId, threadId))
                .then(createBookmark(userId, threadId, request.notes()))
                .flatMap(this::mapToResponse)
                .as(transactionalOperator::transactional);

    }

    @Override
    public Mono<Void> removeBookmark(UUID threadId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return bookmarkRepository.deleteByUserIdAndThreadId(userId, threadId)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<PaginatedResponse<BookmarkResponse>> getMyBookmarks(
            int page,
            int size,
            UUID categoryId,
            UUID creatorId,
            ThreadType threadType,
            ThreadStatus threadStatus,
            Boolean hasContentWarning,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ){

        if(page < 0 || size <= 0){
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        UUID userId = UUID.fromString(viewerContext.getUserId());
        int offset = page * size;

        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();
        String effectiveThreadType =  threadType != null? threadType.name() : null;
        String effectiveThreadStatus  = threadStatus != null? threadStatus.name() : null;
        String effectiveSortBy = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = determineSortDirection(sortDirection, effectiveSortBy);

        return bookmarkRepository.findBookmarkedThreadsPaginated(
                    userId,
                    categoryId, creatorId,
                    effectiveThreadType, effectiveThreadStatus, hasContentWarning,
                    effectiveSearch,
                    effectiveSortBy, effectiveSortDirection,
                    size, offset)
                .flatMap(this::mapToResponse)
                .collectList()
                .zipWith(bookmarkRepository.countBookmarksWithFilters(
                        userId,
                        categoryId, creatorId,
                        effectiveThreadType, effectiveThreadStatus, hasContentWarning,
                        effectiveSearch
                        ))
                .map(tuple -> new PaginatedResponse<>(tuple.getT1(), page, size, tuple.getT2()));

    }

    @Override
    public Mono<Boolean> isBookmarked(UUID threadId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());
        return bookmarkRepository.existsByUserIdAndThreadId(userId, threadId);
    }

    @Override
    public Mono<Long> getBookmarkCountByUserId(ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());
        return bookmarkRepository.countByUserId(userId);
    }

    @Override
    public Mono<Long> getBookmarkCountForThread(UUID threadId){
        return bookmarkRepository.countByThreadId(threadId);
    }


    // ==================== PRIVATE HELPERS ====================

    private Mono<Void> validateThreadExists(UUID threadId) {
        return threadRepository.findByIdAndIsDeletedFalse(threadId)
                .switchIfEmpty(Mono.error(new ApiException("Thread not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .then();
    }

    private Mono<Void> checkNotAlreadyBookmarked(UUID userId, UUID threadId) {
        return bookmarkRepository.existsByUserIdAndThreadId(userId, threadId)
                .flatMap(exists -> {
                    if(exists){
                        return Mono.error(new ApiException("Thread already bookmarked", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<BookmarkedThreadRecord> createBookmark(UUID userId, UUID threadId, String notes){
        ThreadBookmarkEntity bookmarkEntity = ThreadBookmarkEntity.builder()
                .userId(userId)
                .threadId(threadId)
                .notes(notes)
                .build();

        return bookmarkRepository.save(bookmarkEntity)
                .flatMap(bookmark -> bookmarkRepository.findBookmarkById(bookmark.getId(), bookmark.getUserId()));
    }

    private Mono<BookmarkResponse> mapToResponse(BookmarkedThreadRecord record) {
        return appUserService.getUserDetails(record.creator_id())
                        .map(userDetails -> BookmarkResponse.builder()
                                .id(record.bookmark_id())
                                .notes(record.bookmark_notes())
                                .bookmarkedAt(record.bookmarked_at())
                                .categoryId(record.category_id())
                                .threadId(record.thread_id())
                                .threadTitle(record.title())
                                .threadCreatorId(record.creator_id())
                                .threadCreatorDisplayName(userDetails.getDisplayName())
                                .threadCreatorAvatarUrl(userDetails.getAvatarUrl())
                                .threadPostCount(record.post_count())
                                .threadViewCount(record.view_count())
                                .threadLastActivityAt(record.last_activity_at())
                                .threadStatus(ThreadStatus.fromString(record.thread_status()))
                                .threadType(ThreadType.fromString(record.thread_type()))
                                .contentWarningType(ContentWarningType.fromString(record.content_warning_type()))
                                .build());
    }


    private String validateAndNormalizeSortBy(String sortBy) {
        Set<String> allowedFields = Set.of("title", "bookmarked_at", "last_activity_at", "post_count");

        if(sortBy == null || !allowedFields.contains(sortBy)){
            return "bookmarked_at";
        }
        return sortBy;
    }

    private String determineSortDirection(String sortDirection, String effectiveSortBy) {
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        }
        return "DESC";
    }


}
