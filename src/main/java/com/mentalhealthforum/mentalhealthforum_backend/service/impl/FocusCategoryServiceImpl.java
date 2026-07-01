package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.FocusCategoryResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.ThreadCountRecord;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterMetadata;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.SortOption;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.listings.FocusCategorySortField;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.FocusCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ThreadEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.FocusCategoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.CategoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.FocusCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FocusCategoryServiceImpl implements FocusCategoryService {

    private static final Logger log = LoggerFactory.getLogger(FocusCategoryServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final FocusCategoryRepository focusCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final ThreadRepository threadRepository;

    public FocusCategoryServiceImpl(
            TransactionalOperator transactionalOperator,
            FocusCategoryRepository focusCategoryRepository,
            CategoryRepository categoryRepository,
            ThreadRepository threadRepository) {
        this.transactionalOperator = transactionalOperator;
        this.focusCategoryRepository = focusCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.threadRepository = threadRepository;
    }

    @Override
    public Mono<FocusCategoryResponse> addFocusCategory(UUID categoryId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return validateCategoryExists(categoryId)
                .then(checkNotAlreadyFocused(userId, categoryId))
                .then(createFocusCategory(userId, categoryId))
                .flatMap(this::enrichSingleFocusCategoryWithData)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> removeFocusCategory(UUID categoryId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return focusCategoryRepository.deleteByUserIdAndCategoryId(userId, categoryId)
                .as(transactionalOperator::transactional);

    }

    @Override
    public Mono<Boolean> isCategoryFocused(UUID categoryId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return focusCategoryRepository.existsByUserIdAndCategoryId(userId, categoryId);

    }

    @Override
    public Mono<PaginatedResponse<FocusCategoryResponse>> getFocusCategories(
            int page,
            int size,
            Boolean notificationEnabled, String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ){

        if (page < 0 || size <= 0) {
            throw new InvalidPaginationException();
        }

        int offset = page * size;

        UUID viewerId = UUID.fromString(viewerContext.getUserId());
        boolean isAdmin = viewerContext.isAdmin();
        boolean isModeratorOrAdmin = viewerContext.isModeratorOrAdmin();
        boolean isVerified = viewerContext.isVerified();

        String effectiveSearch = (search == null || search.isBlank())? null : search.trim();
        FocusCategorySortField sortByField = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = sortByField.determineSortDirection(sortDirection);

        return focusCategoryRepository.findPaginatedByUserId(
                        viewerId,
                        isAdmin, isModeratorOrAdmin, isVerified,
                        notificationEnabled,
                        effectiveSearch,
                        sortByField.getValue(), effectiveSortDirection,
                        size, offset)
                .collectList()
                .flatMap(focusCategories -> {
                    if(focusCategories.isEmpty()){
                        return Mono.just(new PaginatedResponse<>(List.of(), page, size, 0L));
                    }

                    return enrichFocusCategoriesWithBatchData(focusCategories)
                            .zipWith(focusCategoryRepository.countByUserIdWithFilters(
                                    viewerId,
                                    isAdmin, isModeratorOrAdmin, isVerified,
                                    notificationEnabled,
                                    effectiveSearch))
                            .map(tuple -> {
                                List<FocusCategoryResponse> content = tuple.getT1();
                                long total = tuple.getT2();
                                FilterMetadata<Object> filters = FilterMetadata.builder()
                                        .sortOptions(getFocusCategorySortOptions())
                                        .build();

                                return new PaginatedResponse<>(content, page, size, total, filters);
                            });
                });

    }

    @Override
    public Mono<Long> getFocusCategoriesCount(ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return focusCategoryRepository.countByUserId(userId);
    }

    // ==================== PRIVATE HELPERS ====================

    private Mono<Void> validateCategoryExists(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new ApiException("Category not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .then();
    }

    private Mono<Void> checkNotAlreadyFocused(UUID userId, UUID categoryId){
        return focusCategoryRepository.existsByUserIdAndCategoryId(userId, categoryId)
                .flatMap(exists ->{
                    if(exists){
                        return Mono.error(new ApiException("Category already in your focus list", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<FocusCategoryEntity> createFocusCategory(UUID userId, UUID categoryId){
        FocusCategoryEntity focusCategory = FocusCategoryEntity.builder()
                .userId(userId)
                .categoryId(categoryId)
                .notificationEnabled(false)
                .createdAt(Instant.now())
                .build();
        return  focusCategoryRepository.save(focusCategory);
    }


    private FocusCategorySortField validateAndNormalizeSortBy(String sortBy) {
       return FocusCategorySortField.fromString(sortBy);
    }

    /**
     * Enriches a single focus category with category details and thread count.
     * Uses individual queries since only one item is being fetched.
     */
    private Mono<FocusCategoryResponse> enrichSingleFocusCategoryWithData(FocusCategoryEntity focusCategory){
        return Mono.zip(
                categoryRepository.findById(focusCategory.getCategoryId()),
                threadRepository.countActiveThreadsByCategory(focusCategory.getCategoryId())
        ).map(tuple-> {
            CategoryEntity category = tuple.getT1();
            Long threadCount = tuple.getT2();

            return mapResponseWithData(focusCategory, category, threadCount);
        });
    }
    /**
     * Enriches a list of focus categories with category details and thread counts using batch fetching.
     * Uses batch fetching to avoid N+1 queries.
     */
    private Mono<List<FocusCategoryResponse>> enrichFocusCategoriesWithBatchData(
        List<FocusCategoryEntity> focusCategories
    ){
        if(focusCategories.isEmpty()){
            return Mono.just(List.of());
        }

        List<UUID> categoryIds = focusCategories.stream()
                .map(FocusCategoryEntity::getCategoryId)
                .distinct()
                .toList();

        // Batch fetch category details
        Mono<Map<UUID, CategoryEntity>> categoriesMap = categoryRepository
                .findCategoriesByIds(categoryIds)
                .collectMap(CategoryEntity::getId);

        // Batch fetch thread counts
        Mono<Map<UUID, Long>> threadCountMap = threadRepository
                .findThreadCountsByCategoryIds(categoryIds)
                .collectMap(ThreadCountRecord::category_id, ThreadCountRecord::count)
                .defaultIfEmpty(new HashMap<>());

        return Mono.zip(categoriesMap, threadCountMap)
                .map(tuple -> {
                    Map<UUID, CategoryEntity> categories = tuple.getT1();
                    Map<UUID, Long> threadCounts = tuple.getT2();

                    return focusCategories.stream()
                            .map(focusCategory -> {
                                CategoryEntity category = categories.get(focusCategory.getCategoryId());
                                Long threadCount = threadCounts.getOrDefault(focusCategory.getCategoryId(), 0L);

                                return mapResponseWithData(focusCategory, category, threadCount);
                            })
                            .toList();
                });

    }

    private FocusCategoryResponse mapResponseWithData(
            FocusCategoryEntity focusCategory,
            CategoryEntity category,
            Long threadCount
    ){
        return FocusCategoryResponse.builder()
                .id(focusCategory.getId())
                .notificationEnabled(focusCategory.getNotificationEnabled())
                .focusedAt(focusCategory.getCreatedAt())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .categorySlug(category.getSlug())
                .categoryDescription(category.getDescription())
                .colorTheme(category.getColorTheme())
                .parentCategoryId(category.getParentCategoryId())
                .contentWarningType(category.getContentWarningType())
                .threadCount(threadCount.intValue())
                .isParent(category.isParent())
                .isChild(category.isChild())
                .build();
    }

    private List<SortOption> getFocusCategorySortOptions(){
        return Arrays.stream(FocusCategorySortField.values())
                .map(FocusCategorySortField::toSortOption)
                .collect(Collectors.toList());
    }

}
