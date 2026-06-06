package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkedThreadRecord;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUserEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ThreadBookmarkEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.AppUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ThreadBookmarkRepository;
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
    private final ForumThreadRepository forumThreadRepository;
    private final AppUserRepository appUserRepository;

    public BookmarkServiceImpl(
            TransactionalOperator transactionalOperator,
            ThreadBookmarkRepository bookmarkRepository,
            ForumThreadRepository forumThreadRepository,
            AppUserRepository appUserRepository) {
        this.transactionalOperator = transactionalOperator;
        this.bookmarkRepository = bookmarkRepository;
        this.forumThreadRepository = forumThreadRepository;
        this.appUserRepository = appUserRepository;
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
        String effectiveSortBy = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = determineSortDirection(sortDirection, effectiveSortBy);

        return bookmarkRepository.findBookmarkedThreadsPaginated(userId, effectiveSearch, effectiveSortBy, effectiveSortDirection, size, offset)
                .flatMap(this::mapToResponse)
                .collectList()
                .zipWith(bookmarkRepository.countBookmarksWithFilters(userId,effectiveSearch))
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
        return forumThreadRepository.findByIdAndIsDeletedFalse(threadId)
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

    private Mono<ThreadBookmarkEntity> createBookmark(UUID userId, UUID threadId, String notes){
        ThreadBookmarkEntity bookmark = ThreadBookmarkEntity.builder()
                .userId(userId)
                .threadId(threadId)
                .notes(notes)
                .build();

        return bookmarkRepository.save(bookmark);
    }

    private Mono<BookmarkResponse> mapToResponse(ThreadBookmarkEntity bookmark) {
        return forumThreadRepository.findById(bookmark.getThreadId())
                .switchIfEmpty(Mono.empty())
                .flatMap(thread ->  getCreatorDisplayName(thread.getCreatorId())
                        .map(displayName -> BookmarkResponse.builder()
                                .id(bookmark.getId())
                                .threadId(thread.getId())
                                .threadTitle(thread.getTitle())
                                .threadCreatorId(thread.getCreatorId())
                                .threadCreatorDisplayName(displayName)
                                .threadPostCount(thread.getPostCount())
                                .threadViewCount(thread.getViewCount())
                                .threadLastActivityAt(thread.getLastActivityAt())
                                .notes(bookmark.getNotes())
                                .bookmarkedAt(bookmark.getCreatedAt())
                                .build()));
    }

    private Mono<BookmarkResponse> mapToResponse(BookmarkedThreadRecord record) {
        return  getCreatorDisplayName(record.creator_id())
                        .map(displayName -> BookmarkResponse.builder()
                                .id(record.bookmark_id())
                                .threadId(record.thread_id())
                                .threadTitle(record.title())
                                .threadCreatorId(record.creator_id())
                                .threadCreatorDisplayName(displayName)
                                .threadPostCount(record.post_count())
                                .threadViewCount(record.view_count())
                                .threadLastActivityAt(record.last_activity_at())
                                .notes(record.bookmark_notes())
                                .bookmarkedAt(record.bookmarked_at())
                                .build());
    }

    private Mono<String> getCreatorDisplayName(UUID userId) {
        if(userId == null){
            return Mono.just("System");
        }

        return appUserRepository.findAppUserByKeycloakId(userId.toString())
                .map(AppUserEntity::getPublicIdentifier)
                .defaultIfEmpty("Unknown");
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
