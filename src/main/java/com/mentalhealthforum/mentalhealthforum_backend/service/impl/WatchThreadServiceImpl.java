package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkStatusRecord;
import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.user.UserDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.WatchThreadRecord;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.WatchThreadResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterMetadata;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterOption;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.SortOption;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.WatchThreadFilterDto;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.listings.WatchThreadSortField;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUserEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.WatchThreadEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.WatchThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WatchThreadServiceImpl implements WatchThreadService {

    private static final Logger log = LoggerFactory.getLogger(WatchThreadServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final WatchThreadRepository watchThreadRepository;
    private final ThreadRepository threadRepository;
    private final ThreadBookmarkRepository threadBookmarkRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;
    private final AppUserService appUserService;

    public WatchThreadServiceImpl(
            TransactionalOperator transactionalOperator,
            WatchThreadRepository watchThreadRepository,
            ThreadRepository threadRepository,
            ThreadBookmarkRepository threadBookmarkRepository,
            CategoryRepository categoryRepository,
            AppUserRepository appUserRepository,
            AppUserService appUserService) {
        this.transactionalOperator = transactionalOperator;
        this.watchThreadRepository = watchThreadRepository;
        this.threadRepository = threadRepository;
        this.threadBookmarkRepository = threadBookmarkRepository;
        this.categoryRepository = categoryRepository;
        this.appUserRepository = appUserRepository;
        this.appUserService = appUserService;
    }

    @Override
    public Mono<WatchThreadResponse> watchThread(UUID threadId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return validateThreadExists(threadId)
                .then(checkNotAlreadyWatching(userId, threadId))
                .then(createWatch(userId, threadId))
                .flatMap(watchThread -> enrichSingleWatchedThreadWithData(watchThread, userId))
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

        if(page < 0 || size <= 0){
            throw new InvalidPaginationException();
        }

        int offset = page * size;

        UUID viewerId = UUID.fromString(viewerContext.getUserId());
        boolean isAdmin = viewerContext.isAdmin();
        boolean isModeratorOrAdmin = viewerContext.isModeratorOrAdmin();
        boolean isVerified = viewerContext.isVerified();

        String effectiveThreadType =  threadType != null? threadType.name() : null;
        String effectiveThreadStatus  = threadStatus != null? threadStatus.name() : null;
        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();
        WatchThreadSortField sortByField = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = sortByField.determineSortDirection(sortDirection);

        return watchThreadRepository.findPaginatedByUserId(
                viewerId,
                isAdmin, isModeratorOrAdmin, isVerified,
                categoryId, creatorId, effectiveThreadType, effectiveThreadStatus,
                hasContentWarning, isBookmarked, notificationEnabled,
                effectiveSearch,
                sortByField.getValue(), effectiveSortDirection,
                size, offset
                )
                .collectList()
                .flatMap(records -> {
                    if(records.isEmpty()){
                        return Mono.just(new PaginatedResponse<>(List.of(), page, size, 0L));
                    }
                    return enrichWatchedThreadsWithBatchData(records, viewerId)
                            .zipWith(watchThreadRepository.countByUserIdWithFilters(
                                    viewerId,
                                    isAdmin, isModeratorOrAdmin, isVerified,
                                    categoryId, creatorId, effectiveThreadType, effectiveThreadStatus,
                                    hasContentWarning, isBookmarked, notificationEnabled,
                                    effectiveSearch)
                            )
                            .map(tuple -> {
                                EnrichedWatchThreadData enrichedData = tuple.getT1();
                                long total = tuple.getT2();
                                FilterMetadata<WatchThreadFilterDto> filters = buildWatchThreadFilter(enrichedData);

                                return new PaginatedResponse<>(enrichedData.responses, page, size, total, filters);
                            });
                });

    }

    @Override
    public Mono<Long> getWatchThreadCount(ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return watchThreadRepository.countByUserId(userId);
    }

    // ==================== PRIVATE HELPERS ====================

    private Mono<Void> validateThreadExists(UUID threadId) {
        return threadRepository.existsById(threadId)
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

    private WatchThreadSortField validateAndNormalizeSortBy(String sortBy) {
       return WatchThreadSortField.fromString(sortBy);
    }

    /**
     * Enriches single watch thread.
     */
    private Mono<WatchThreadResponse> enrichSingleWatchedThreadWithData(WatchThreadRecord record, UUID userId) {
        return Mono.zip(
                appUserService.getUserDetails(record.creator_id()),
                threadBookmarkRepository.existsByUserIdAndThreadId(userId, record.thread_id())
        ).map(tuple -> {
            UserDetails creator = tuple.getT1();
            Boolean isBookmarked = tuple.getT2();

            return mapResponseWithData(record, creator, isBookmarked);
        });
    }

    /**
     * Enriches a list of watched thread records with creator details using batch fetching.
     * Uses batch fetching to avoid N+1 queries.
     */
    private Mono<EnrichedWatchThreadData> enrichWatchedThreadsWithBatchData(
        List<WatchThreadRecord> records,
        UUID userId
    ){
        if(records.isEmpty()){
            return Mono.just(new EnrichedWatchThreadData(
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of()
            ));
        }

        // Extract unique creator IDs
        List<UUID> creatorIds = records.stream()
                .map(WatchThreadRecord::creator_id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Extract unique category IDs
        List<UUID> categoryIds = records.stream()
                .map(WatchThreadRecord::category_id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Batch fetch creator details
        Mono<Map<UUID, UserDetails>> creatorsMap = appUserRepository
                .findAppUsersByKeycloakIds(creatorIds)
                .collectMap(AppUserEntity::getKeycloakId, AppUserEntity::toUserDetails)
                .defaultIfEmpty(new HashMap<>());

        // Batch fetch category details
        Mono<Map<UUID, CategoryEntity>> categoriesMap = categoryRepository
                .findCategoriesByIds(categoryIds)
                .collectMap(CategoryEntity::getId)
                .defaultIfEmpty(new HashMap<>());

        // Batch fetch bookmark status (for each watched thread)
        List<UUID> threadIds = records.stream()
                .map(WatchThreadRecord::thread_id)
                .toList();

        Mono<Map<UUID, Boolean>> bookmarkStatusMap = threadBookmarkRepository
                .findBookmarkStatusForThreads(userId, threadIds)
                .collectMap(BookmarkStatusRecord::thread_id, BookmarkStatusRecord::is_bookmarked)
                .defaultIfEmpty(new HashMap<>());

        return Mono.zip(creatorsMap, categoriesMap, bookmarkStatusMap)
                .map(tuple -> {
                    Map<UUID, UserDetails> creators = tuple.getT1();
                    Map<UUID, CategoryEntity> categories = tuple.getT2();
                    Map<UUID, Boolean> bookmarkStatus = tuple.getT3();

                    List<WatchThreadResponse> responses = records.stream()
                            .map(record -> {
                                UserDetails creator = creators.get(record.creator_id());
                                Boolean isBookmarked = bookmarkStatus.getOrDefault(record.thread_id(), false);

                                return mapResponseWithData(record, creator, isBookmarked);
                            })
                            .toList();

                    return new EnrichedWatchThreadData(
                            responses,
                            records,
                            creators,
                            categories
                    );
                });
    }

    private WatchThreadResponse mapResponseWithData(
        WatchThreadRecord record,
        UserDetails creator,
        Boolean isBookmarked
    ){

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
                .creatorDisplayName(creator.getDisplayName())
                .creatorAvatarUrl(creator.getAvatarUrl())
                .postCount(record.post_count())
                .viewCount(record.view_count())
                .lastActivityAt(record.last_activity_at())
                .contentWarningType(ContentWarningType.fromString(record.content_warning_type()))
                .isOpen(ThreadStatus.fromString(record.thread_status()) == ThreadStatus.OPEN)
                .isBookmarked(isBookmarked)
                .isSticky(record.is_sticky())
                .isFeatured(record.is_featured())
                .build();
    }

    private record EnrichedWatchThreadData(
            List<WatchThreadResponse> responses,
            List<WatchThreadRecord> records,
            Map<UUID, UserDetails> creators,
            Map<UUID, CategoryEntity> categories
    ) {}

    /**
     * Builds filter metadata from enriched watch thread data.
     */
    private FilterMetadata<WatchThreadFilterDto> buildWatchThreadFilter(EnrichedWatchThreadData data){
        // Builder creator options
        Map<UUID, Long> creatorCounts = data.records().stream()
                .collect(Collectors.groupingBy(
                        WatchThreadRecord::creator_id,
                        Collectors.counting()
                ));

        List<FilterOption> creatorOptions = data.creators.entrySet().stream()
                .map(entry -> {
                    UUID creatorId = entry.getKey();
                    UserDetails creator = entry.getValue();
                    long count = creatorCounts.getOrDefault(creatorId, 0L);
                    return new FilterOption(
                            creatorId,
                            creator.getDisplayName(),
                            creatorId.toString(),
                            creator.getAvatarUrl(),
                            count
                    );
                })
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();

        // Builder category options
        Map<UUID, Long> categoryCounts = data.records().stream()
                .collect(Collectors.groupingBy(
                        WatchThreadRecord::category_id,
                        Collectors.counting()
                ));

        List<FilterOption> categoryOptions = data.categories().entrySet().stream()
                .map(entry -> {
                    UUID categoryId = entry.getKey();
                    CategoryEntity category = entry.getValue();
                    long count = categoryCounts.getOrDefault(categoryId, 0L);
                    return new FilterOption(
                            categoryId,
                            category.getName(),
                            category.getSlug(),
                            count
                    );
                })
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();

        WatchThreadFilterDto watchThreadFilters = WatchThreadFilterDto.builder()
                .creators(creatorOptions)
                .categories(categoryOptions)
                .build();

        return FilterMetadata.<WatchThreadFilterDto>builder()
                .filters(watchThreadFilters)
                .sortOptions(getWatchThreadSortOptions())
                .build();

    }

    private List<SortOption> getWatchThreadSortOptions() {
        return Arrays.stream(
                WatchThreadSortField.values())
                .map(WatchThreadSortField::toSortOption)
                .toList();
    }

}
