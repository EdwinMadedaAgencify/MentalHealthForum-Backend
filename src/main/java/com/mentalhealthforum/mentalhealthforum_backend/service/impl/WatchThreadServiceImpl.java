package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.UserDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.WatchThreadRecord;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.WatchThreadResponse;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.WatchThreadEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ThreadBookmarkRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.WatchThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.WatchThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

@Service
public class WatchThreadServiceImpl implements WatchThreadService {

    private static final Logger log = LoggerFactory.getLogger(WatchThreadServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final WatchThreadRepository watchThreadRepository;
    private final ForumThreadRepository forumThreadRepository;
    private final ThreadBookmarkRepository threadBookmarkRepository;
    private final AppUserService appUserService;

    public WatchThreadServiceImpl(
            TransactionalOperator transactionalOperator,
            WatchThreadRepository watchThreadRepository,
            ForumThreadRepository forumThreadRepository,
            ThreadBookmarkRepository threadBookmarkRepository,
            AppUserService appUserService) {
        this.transactionalOperator = transactionalOperator;
        this.watchThreadRepository = watchThreadRepository;
        this.forumThreadRepository = forumThreadRepository;
        this.threadBookmarkRepository = threadBookmarkRepository;
        this.appUserService = appUserService;
    }

    @Override
    public Mono<WatchThreadResponse> watchThread(UUID threadId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return validateThreadExists(threadId)
                .then(checkNotAlreadyWatching(userId, threadId))
                .then(createWatch(userId, threadId))
                .flatMap(watchThread -> mapToResponse(watchThread, userId))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> unwatchThread(UUID threadId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return watchThreadRepository.deleteByUserIdAndThreadId(userId, threadId)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Boolean> isWatchingThread(UUID threadId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return watchThreadRepository.existsByUserIdAndThreadId(userId, threadId);
    }

    @Override
    public Mono<PaginatedResponse<WatchThreadResponse>> getWatchThreads(
            int page,
            int size,
            UUID categoryId,
            UUID creatorId,
            ThreadType threadType,
            ThreadStatus threadStatus,
            Boolean hasContentWarning,
            Boolean isBookmarked,
            Boolean notificationEnabled,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        if(page < 0 || size <= 0){
            throw new InvalidPaginationException();
        }

        int offset = page * size;

        String effectiveThreadType =  threadType != null? threadType.name() : null;
        String effectiveThreadStatus  = threadStatus != null? threadStatus.name() : null;
        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();
        String effectiveSortBy = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = determineSortDirection(sortDirection, effectiveSortBy);

        return watchThreadRepository.findPaginatedByUserId(
                userId,
                categoryId, creatorId, effectiveThreadType, effectiveThreadStatus,
                hasContentWarning, isBookmarked, notificationEnabled,
                effectiveSearch,
                effectiveSortBy, effectiveSortDirection,
                size, offset
                )
                .flatMap(watchThread -> mapToResponse(watchThread, userId))
                .collectList()
                .zipWith(watchThreadRepository.countByUserIdWithFilters(
                        userId,
                        categoryId, creatorId, effectiveThreadType, effectiveThreadStatus,
                        hasContentWarning, isBookmarked, notificationEnabled,
                        effectiveSearch)
                )
                .map(tuple ->new PaginatedResponse<>(tuple.getT1(), page, size, tuple.getT2()));

    }

    @Override
    public Mono<Long> getWatchThreadCount(ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return watchThreadRepository.countByUserId(userId);
    }

    // ==================== PRIVATE HELPERS ====================

    private Mono<Void> validateThreadExists(UUID threadId) {
        return forumThreadRepository.existsById(threadId)
                .flatMap(exists -> {
                    if(!exists){
                        return Mono.error(new ApiException("Thread not found", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> checkNotAlreadyWatching(UUID userId, UUID threadId) {
        return watchThreadRepository.existsByUserIdAndThreadId(userId, threadId)
                .flatMap(exists -> {
                    if(exists){
                        return Mono.error(new ApiException("Already watching this thread", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<WatchThreadRecord> createWatch(UUID userId, UUID threadId) {
        WatchThreadEntity watchThreadEntity = WatchThreadEntity.builder()
                .userId(userId)
                .threadId(threadId)
                .notificationEnabled(false)
                .build();
        return watchThreadRepository.save(watchThreadEntity)
                .flatMap(watchThread -> watchThreadRepository.findWatchById(watchThread.getId(), userId));
    }

    private Mono<WatchThreadResponse> mapToResponse(WatchThreadRecord record, UUID userId) {
        return Mono.zip(
            appUserService.getUserDetails(record.creator_id()),
            threadBookmarkRepository.existsByUserIdAndThreadId(userId, record.thread_id())
        ).map(tuple -> {
            UserDetails userDetails = tuple.getT1();
            Boolean isBookmarked = tuple.getT2();

            return WatchThreadResponse.builder()
                    .id(record.watch_id())
                    .notificationEnabled(record.notification_enabled())
                    .watchedAt(record.watched_at())
                    .threadId(record.thread_id())
                    .threadTitle(record.thread_title())
                    .threadType(ThreadType.fromString(record.thread_type()))
                    .threadStatus(ThreadStatus.fromString(record.thread_status()))
                    .categoryId(record.category_id())
                    .creatorId(record.creator_id())
                    .creatorDisplayName(userDetails.getDisplayName())
                    .creatorAvatarUrl(userDetails.getAvatarUrl())
                    .postCount(record.post_count())
                    .viewCount(record.view_count())
                    .lastActivityAt(record.last_activity_at())
                    .contentWarningType(ContentWarningType.fromString(record.content_warning_type()))
                    .isOpen(ThreadStatus.fromString(record.thread_status()) == ThreadStatus.OPEN)
                    .isBookmarked(isBookmarked)
                    .isSticky(record.is_sticky())
                    .isFeatured(record.is_featured())
                    .build();
        });
    }

    private String validateAndNormalizeSortBy(String sortBy) {
        Set<String> allowedFields = Set.of("created_at", "last_activity_at", "thread_title", "post_count", "view_count");
        if(sortBy == null || !allowedFields.contains(sortBy)){
            return "created_at";
        }
        return sortBy;
    }

    private String determineSortDirection(String sortDirection, String effectiveSortBy){
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        }
        return "DESC";
    }
}
