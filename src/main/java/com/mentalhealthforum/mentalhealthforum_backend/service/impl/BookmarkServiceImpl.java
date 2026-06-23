package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.BookmarkedThreadRecord;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.UserDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.BookmarkFilterDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterMetadata;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterOption;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.SortOption;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadStatus;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ThreadType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.listings.BookmarkSortField;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUserEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ThreadBookmarkEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.AppUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.CategoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ThreadBookmarkRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.BookmarkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import reactor.core.publisher.Mono;

import java.util.*;

import java.util.stream.Collectors;

@Service
public class BookmarkServiceImpl implements BookmarkService {

    private final TransactionalOperator transactionalOperator;
    private final ThreadBookmarkRepository bookmarkRepository;
    private final ThreadRepository threadRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;
    private final AppUserService appUserService;

    public BookmarkServiceImpl(
            TransactionalOperator transactionalOperator,
            ThreadBookmarkRepository bookmarkRepository,
            ThreadRepository threadRepository,
            CategoryRepository categoryRepository,
            AppUserRepository appUserRepository,
            AppUserService appUserService) {
        this.transactionalOperator = transactionalOperator;
        this.bookmarkRepository = bookmarkRepository;
        this.threadRepository = threadRepository;
        this.categoryRepository = categoryRepository;
        this.appUserRepository = appUserRepository;
        this.appUserService = appUserService;
    }

    @Override
    public Mono<BookmarkResponse> addBookmark(BookmarkRequest request, ViewerContext viewerContext){

        UUID userId = UUID.fromString(viewerContext.getUserId());
        UUID threadId = request.threadId();

        return validateThreadExists(threadId)
                .then(checkNotAlreadyBookmarked(userId, threadId))
                .then(createBookmark(userId, threadId, request.notes()))
                .flatMap(this::enrichSingleBookmarkWithData)
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
        BookmarkSortField sortByField = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = sortByField.determineSortDirection(sortDirection);


        return bookmarkRepository.findBookmarkedThreadsPaginated(
                    userId,
                    categoryId, creatorId,
                    effectiveThreadType, effectiveThreadStatus, hasContentWarning,
                    effectiveSearch,
                    sortByField.getValue(), effectiveSortDirection,
                    size, offset)
                .collectList()
                .flatMap(records -> {
                    if(records.isEmpty()){
                        return Mono.just(new PaginatedResponse<>(List.of(), page, size, 0L));
                    }

                    return enrichBookmarksWithBatchData(records)
                            .zipWith(bookmarkRepository.countBookmarksWithFilters(
                                    userId,
                                    categoryId, creatorId,
                                    effectiveThreadType, effectiveThreadStatus, hasContentWarning,
                                    effectiveSearch
                            ))
                            .map(tuple -> {
                                EnrichedBookmarkData enrichedBookmarkData = tuple.getT1();
                                long totalCount = tuple.getT2();

                                FilterMetadata<BookmarkFilterDto> filters = buildBookmarkFilters(enrichedBookmarkData);

                                return new PaginatedResponse<>(enrichedBookmarkData.responses, page, size, totalCount, filters);
                            });
                });

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

    private BookmarkSortField validateAndNormalizeSortBy(String sortBy) {
       return BookmarkSortField.fromString(sortBy);
    }


    /**
     * Enriches a single bookmark..
     */
    private Mono<BookmarkResponse> enrichSingleBookmarkWithData(BookmarkedThreadRecord record) {
        return appUserService.getUserDetails(record.creator_id())
                .map(creator -> mapResponseWithData(record, creator));
    }
    /**
     * Enriches a list of bookmarked thread records with creator details using batch fetching.
     * Uses batch fetching to avoid N+1 queries.
     */
    private Mono<EnrichedBookmarkData> enrichBookmarksWithBatchData(
        List<BookmarkedThreadRecord> records
    ){
        if(records.isEmpty()){
            return Mono.just(new EnrichedBookmarkData(
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of()
            ));
        }

        // Extract unique creator IDs
        List<UUID> creatorIds = records.stream()
                .map(BookmarkedThreadRecord::creator_id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Extract unique category IDs
        List<UUID> categoryIds = records.stream()
                .map(BookmarkedThreadRecord::category_id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Batch fetch all creators
        Mono<Map<UUID, UserDetails>> creatorsMap = appUserRepository
                .findAppUsersByKeycloakIds(creatorIds)
                .collectMap(AppUserEntity::getKeycloakId, AppUserEntity::toUserDetails)
                .defaultIfEmpty(new HashMap<>());

        // Batch fetch all categories
        Mono<Map<UUID, CategoryEntity>> categoriesMap = categoryRepository
                .findCategoriesByIds(categoryIds)
                .collectMap(CategoryEntity::getId)
                .defaultIfEmpty(new HashMap<>());


        return Mono.zip(
                creatorsMap,
                categoriesMap
        ).map(tuple -> {
            Map<UUID, UserDetails> creators = tuple.getT1();
            Map<UUID, CategoryEntity> categories = tuple.getT2();

            List<BookmarkResponse> responses = records.stream()
                    .map(record -> {
                        UserDetails creator = creators.get(record.creator_id());
                        return mapResponseWithData(record, creator);
                    })
                    .toList();

            return new EnrichedBookmarkData(
                    responses,
                    records,
                    creators,
                    categories
            );
        });
    }

    private BookmarkResponse mapResponseWithData(
        BookmarkedThreadRecord record,
        UserDetails creator
    ){
        return BookmarkResponse.builder()
                .id(record.bookmark_id())
                .notes(record.bookmark_notes())
                .bookmarkedAt(record.bookmarked_at())
                .categoryId(record.category_id())
                .threadId(record.thread_id())
                .threadTitle(record.title())
                .threadCreatorId(record.creator_id())
                .threadCreatorDisplayName(creator.getDisplayName())
                .threadCreatorAvatarUrl(creator.getAvatarUrl())
                .threadPostCount(record.post_count())
                .threadViewCount(record.view_count())
                .threadLastActivityAt(record.last_activity_at())
                .threadStatus(ThreadStatus.fromString(record.thread_status()))
                .threadType(ThreadType.fromString(record.thread_type()))
                .contentWarningType(ContentWarningType.fromString(record.content_warning_type()))
                .build();
    }

    private record EnrichedBookmarkData(
            List<BookmarkResponse> responses,
            List<BookmarkedThreadRecord> records,
            Map<UUID, UserDetails> creators,
            Map<UUID, CategoryEntity> categories
    ) {}

    private FilterMetadata<BookmarkFilterDto> buildBookmarkFilters(EnrichedBookmarkData data){
        // Build creator options
        Map<UUID, Long> creatorCounts = data.records.stream()
                .collect(Collectors.groupingBy(
                        BookmarkedThreadRecord::creator_id,
                        Collectors.counting()
                ));

        List<FilterOption> creatorOptions = data.creators().entrySet().stream()
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

        // Build category options
        Map<UUID, Long> categoryCounts = data.records.stream()
                .collect(Collectors.groupingBy(
                        BookmarkedThreadRecord::category_id,
                        Collectors.counting()
                ));

        List<FilterOption> categoryOptions = data.categories.entrySet().stream()
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

        BookmarkFilterDto bookmarkFilters = BookmarkFilterDto.builder()
                .creators(creatorOptions)
                .categories(categoryOptions)
                .build();

        return FilterMetadata.<BookmarkFilterDto>builder()
                .filters(bookmarkFilters)
                .sortOptions(getBookmarkSortOptions())
                .build();

    }

    private List<SortOption> getBookmarkSortOptions() {
        return Arrays.stream(
                BookmarkSortField.values())
                .map(BookmarkSortField::toSortOption)
                .toList();
    }

}
