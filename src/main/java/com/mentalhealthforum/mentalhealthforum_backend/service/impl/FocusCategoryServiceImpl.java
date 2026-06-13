package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.FocusCategoryResponse;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.FocusCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.FocusCategoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumCategoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.FocusCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class FocusCategoryServiceImpl implements FocusCategoryService {

    private static final Logger log = LoggerFactory.getLogger(FocusCategoryServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final FocusCategoryRepository focusCategoryRepository;
    private final ForumCategoryRepository forumCategoryRepository;
    private final ForumThreadRepository forumThreadRepository;

    public FocusCategoryServiceImpl(
            TransactionalOperator transactionalOperator,
            FocusCategoryRepository focusCategoryRepository,
            ForumCategoryRepository forumCategoryRepository,
            ForumThreadRepository forumThreadRepository) {
        this.transactionalOperator = transactionalOperator;
        this.focusCategoryRepository = focusCategoryRepository;
        this.forumCategoryRepository = forumCategoryRepository;
        this.forumThreadRepository = forumThreadRepository;
    }

    @Override
    public Mono<FocusCategoryResponse> addFocusCategory(UUID categoryId, ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return validateCategoryExists(categoryId)
                .then(checkNotAlreadyFocused(userId, categoryId))
                .then(createFocusCategory(userId, categoryId))
                .flatMap(this::mapToResponse)
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
        UUID userId = UUID.fromString(viewerContext.getUserId());
        String effectiveSearch = (search == null || search.isBlank())? null : search.trim();
        String effectiveSortBy = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = determineSortDirection(sortDirection, effectiveSortBy);

        return focusCategoryRepository.findPaginatedByUserId(userId, notificationEnabled, effectiveSearch, effectiveSortBy, effectiveSortDirection, size, offset)
                .flatMap(this::mapToResponse)
                .collectList()
                .zipWith(focusCategoryRepository.countByUserIdWithFilters(userId, notificationEnabled, effectiveSearch))
                .map(tuple -> new PaginatedResponse<>(tuple.getT1(), page, size, tuple.getT2()));

    }

    private String determineSortDirection(String sortDirection, String effectiveSortBy) {
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection)? "DESC" : "ASC";
        }
        return "DESC";
    }

    @Override
    public Mono<Long> getFocusCategoriesCount(ViewerContext viewerContext){
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return focusCategoryRepository.countByUserId(userId);
    }

    // ==================== PRIVATE HELPERS ====================

    private Mono<Void> validateCategoryExists(UUID categoryId) {
        return forumCategoryRepository.findById(categoryId)
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

    private Mono<FocusCategoryResponse> mapToResponse(FocusCategoryEntity focusCategory){
        return Mono.zip(
                forumCategoryRepository.findById(focusCategory.getCategoryId()),
                forumThreadRepository.countActiveThreadsByCategory(focusCategory.getCategoryId())
        ).map(tuple-> {
            ForumCategoryEntity category = tuple.getT1();
            Long threadCount = tuple.getT2();

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
        });
    }

    private String validateAndNormalizeSortBy(String sortBy) {
        Set<String> allowedFields = Set.of("created_at", "category_name");
        if(sortBy == null || !allowedFields.contains(sortBy)){
            return "created_at";
        }
        return sortBy;
    }

}
