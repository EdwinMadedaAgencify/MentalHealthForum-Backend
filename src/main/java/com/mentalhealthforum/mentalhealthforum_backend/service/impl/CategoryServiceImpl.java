package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.ThreadCountRecord;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.CategoryFilterDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterMetadata;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.FilterOption;
import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.SortOption;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ModerationAction;
import com.mentalhealthforum.mentalhealthforum_backend.enums.listings.CategorySortField;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.*;
import com.mentalhealthforum.mentalhealthforum_backend.service.CategoryService;
import com.mentalhealthforum.mentalhealthforum_backend.service.CategoryTagService;
import com.mentalhealthforum.mentalhealthforum_backend.service.FocusCategoryService;
import com.mentalhealthforum.mentalhealthforum_backend.utils.SlugsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final CategoryRepository categoryRepository;
    private final ThreadRepository threadRepository;
    private final CategoryTagService categoryTagService;
    private final CategoryTagRepository categoryTagRepository;
    private final CategoryTagAssignmentRepository categoryTagAssignmentRepository;
    private final FocusCategoryRepository focusCategoryRepository;
    private final FocusCategoryService focusCategoryService;


    public CategoryServiceImpl(TransactionalOperator transactionalOperator,
                               CategoryRepository categoryRepository,
                               ThreadRepository threadRepository,
                               CategoryTagService categoryTagService,
                               CategoryTagRepository categoryTagRepository,
                               CategoryTagAssignmentRepository categoryTagAssignmentRepository,
                               FocusCategoryRepository focusCategoryRepository,
                               FocusCategoryService focusCategoryService) {
        this.transactionalOperator = transactionalOperator;
        this.categoryRepository = categoryRepository;
        this.threadRepository = threadRepository;
        this.categoryTagService = categoryTagService;
        this.categoryTagRepository = categoryTagRepository;
        this.categoryTagAssignmentRepository = categoryTagAssignmentRepository;
        this.focusCategoryRepository = focusCategoryRepository;
        this.focusCategoryService = focusCategoryService;
    }

    // ==================== SLUG GENERATION ====================

    @Override
    public Mono<SlugGenerationResponse> generateCategorySlug(String name, UUID excludeCategoryId){
        String baseSlug = SlugsUtil.generateSlug(name);

        if(baseSlug.isEmpty()){
            return Mono.error(new ApiException(
                    "Could not generate slug from provided name",
                    ErrorCode.INVALID_SLUG_GENERATION
            ));
        }

        Function<String, Mono<Boolean>> existsCheck = slug -> excludeCategoryId == null
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, excludeCategoryId);

        return SlugsUtil.generateUniqueSlugReactive(name, existsCheck)
                .map(uniqueSlug -> new SlugGenerationResponse(uniqueSlug, uniqueSlug.equals(baseSlug)));

    }

    // ==================== CATEGORY CRUD ====================

    @Override
    public Mono<CategoryResponse> createCategory(CreateCategoryRequest request, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_CREATED.checkPermission(viewerContext)
                .then(doCreateCategory(request, viewerContext))
                .flatMap(category -> enrichSingleCategoryWithData(category, viewerContext));
    }

    private Mono<CategoryEntity> doCreateCategory(CreateCategoryRequest request, ViewerContext viewerContext) {

        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = SlugsUtil.generateSlug(request.getName());
        }
        String finalSlug = slug;

        return validateParentCategory(request.getParentCategoryId())
                .then(validateNoDuplicates(request.getName(), finalSlug))
                .then(createAndSaveCategory(request, finalSlug))
                .flatMap(savedCategory -> categoryTagService.addTagsToCategory(savedCategory, request.getTagIds(), viewerContext)
                        .thenReturn(savedCategory)
                )
                .as(transactionalOperator::transactional);

    }

    @Override
    public Mono<CategoryResponse> updateCategory(UUID id, UpdateCategoryRequest request, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_UPDATED.checkPermission(viewerContext)
                .then(doUpdateCategory(id, request, viewerContext))
                .flatMap(category -> enrichSingleCategoryWithData(category, viewerContext));
    }

    private Mono<CategoryEntity> doUpdateCategory(UUID id, UpdateCategoryRequest request, ViewerContext viewerContext) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '" + id + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(existingCategory -> {
                    String newSlug = request.getSlug();
                    if (newSlug != null && !newSlug.equals(existingCategory.getSlug())) {
                        return categoryRepository.existsBySlugAndIdNot(newSlug, id)
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new ApiException(
                                                "Slug '" + newSlug + "' already exists",
                                                ErrorCode.DUPLICATE_CATEGORY_SLUG));
                                    }
                                    return updateCategoryFields(existingCategory, request, viewerContext);
                                });
                    }
                    return updateCategoryFields(existingCategory, request, viewerContext);
                });
    }

    @Override
    public Mono<Void> softDeleteCategory(UUID id, ViewerContext viewerContext) {
       return ModerationAction.CATEGORY_SOFT_DELETED.checkPermission(viewerContext)
                .then(doSoftDeleteCategory(id));

    }

    private Mono<Void> doSoftDeleteCategory(UUID id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '" + id + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category -> {
                    if (!category.getIsActive()) {
                        return Mono.error(new ApiException(
                                "Category is already soft deleted",
                                ErrorCode.VALIDATION_FAILED));
                    }
                    category.setIsActive(false);
                    return categoryRepository.save(category).then();
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<CategoryResponse> reactivateCategory(UUID id, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_REACTIVATED.checkPermission(viewerContext)
                .then(doReactivateCategory(id))
                .flatMap(category -> enrichSingleCategoryWithData(category, viewerContext));
    }

    private Mono<CategoryEntity> doReactivateCategory(UUID id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category -> {
                    if (category.getIsActive()) {
                        return Mono.error(new ApiException(
                                "Category is already active",
                                ErrorCode.VALIDATION_FAILED));
                    }

                    // Check if parent is active (if it has a parent)
                    if (category.getParentCategoryId() != null) {
                        // Prevent self-reference
                        if (category.getParentCategoryId().equals(category.getId())) {
                            return Mono.error(new ApiException(
                                    "Category cannot be its own parent",
                                    ErrorCode.INVALID_HIERARCHY));
                        }

                        return categoryRepository.findById(category.getParentCategoryId())
                                .flatMap(parent -> {
                                    if (!parent.getIsActive()) {
                                        return Mono.error(new ApiException(
                                                "Cannot reactivate: Parent category is inactive",
                                                ErrorCode.INVALID_HIERARCHY));
                                    }

                                    category.setIsActive(true);
                                    return categoryRepository.save(category);
                                });
                    }
                    category.setIsActive(true);
                    return categoryRepository.save(category);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> purgeCategory(UUID categoryId, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_PURGED.checkPermission(viewerContext)
                .then(doPurgeCategory(categoryId, viewerContext));
    }

    private Mono<Void> doPurgeCategory(UUID categoryId, ViewerContext viewerContext) {
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category -> {
                    // Check if category has active children (can't purge if it has active children)
                    return categoryRepository.findChildCategories(categoryId)
                            .filter(CategoryEntity::getIsActive)
                            .hasElements()
                            .flatMap(hasActiveElements -> {
                                if (hasActiveElements) {
                                    return Mono.error(new ApiException(
                                            "Cannot purge category with active child categories",
                                            ErrorCode.INVALID_HIERARCHY));
                                }

                                return threadRepository.existsByCategoryId(categoryId)
                                        .flatMap(hasThreads -> {
                                            if(hasThreads){
                                                return Mono.error(new ApiException(
                                                        "Cannot purge category with existing threads. Delete or move threads first.",
                                                        ErrorCode.VALIDATION_FAILED
                                                ));
                                            }

                                            // Delete associated tags first, then delete category
                                            return categoryTagService.deleteAllTagAssignmentsForCategory(categoryId, viewerContext)
                                                    .then(categoryRepository.delete(category));
                                        });

                                // TODO: When other dependencies are added (subscriptions, analytics, etc.),
                                // add similar checks here to prevent orphaned records.
                            });
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> purgeOldInactiveCategories(int daysOld, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_PURGE_OLD.checkPermission(viewerContext)
                .then(doPurgeOldInactiveCategories(daysOld));
    }

    @Override
    public Mono<Void> purgeOldInactiveCategoriesInternal(int daysOld) {
        return doPurgeOldInactiveCategories(daysOld);
    }

    private Mono<Void> doPurgeOldInactiveCategories(int daysOld) {
        Instant cutoffDate = Instant.now().minus(Duration.ofDays(daysOld));

        return categoryRepository.findInactiveCategoriesOlderThan(cutoffDate)
                .flatMap(category -> {
                    // Delete tags first, then category
                    return categoryTagAssignmentRepository.deleteByCategoryId(category.getId())
                            .then(categoryRepository.delete(category));
                })
                .then()
                .doOnSuccess(v -> log.info("Purged inactive categories older than {} days", daysOld))
                .doOnError(e -> log.error("Error purging old categories: {}", e.getMessage()));
    }

    // ==================== QUERIES (with permission checks where needed) ====================

    @Override
    public Mono<CategoryResponse> getCategoryById(UUID id, ViewerContext viewerContext) {
        // Public - no permission check needed
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '" + id + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category -> enrichSingleCategoryWithData(category, viewerContext));
    }

    @Override
    public Mono<CategoryResponse> getCategoryBySlug(String slug, ViewerContext viewerContext) {
        // Public - no permission check needed
        return categoryRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with slug '" + slug + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category -> enrichSingleCategoryWithData(category, viewerContext));
    }

    @Override
    public Mono<PaginatedResponse<CategoryResponse>> getActiveCategories(
            int page,
            int size,
            UUID tagId,
            UUID parentCategoryId,
            Boolean isParent,
            Boolean isFocused,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext) {
        // Public - no permission check needed
        return executeGetCategoriesQuery(page, size, tagId, parentCategoryId, isParent, true, isFocused, search , sortBy, sortDirection, viewerContext);
    }

    @Override
    public Mono<PaginatedResponse<CategoryResponse>> getAllCategories(
            int page,
            int size,
            UUID tagId,
            UUID parentCategoryId,
            Boolean isParent,
            Boolean isActive,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ) {
        return ModerationAction.CATEGORY_VIEW_INACTIVE.checkPermission(viewerContext)
                .then(executeGetCategoriesQuery(page, size, tagId, parentCategoryId, isParent, isActive, null, search, sortBy, sortDirection, viewerContext));

    }

    @Override
    public Mono<Long> getInactiveCount(ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_VIEW_INACTIVE.checkPermission(viewerContext)
                .then(categoryRepository.countByIsActiveFalse());
    }

    // ==================== HIERARCHY ====================

    @Override
    public Flux<CategoryHierarchyDto> getCategoryHierarchy(ViewerContext viewerContext) {
        // Public - no permission check needed
        return categoryRepository.findRootCategories()
                .flatMap(root -> Mono.zip(
                        enrichSingleCategoryWithData(root, viewerContext),
                        categoryRepository.findChildCategories(root.getId())
                                .flatMap(category -> enrichSingleCategoryWithData(category, viewerContext))
                                .collectList(),
                        categoryTagService.getTagsForCategory(root.getId())
                                .collectList()
                ))
                .map(tuple -> new CategoryHierarchyDto(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ));
    }

    @Override
    public Flux<CategoryResponse> getRootCategories(ViewerContext viewerContext) {
        // Public - no permission check needed
        return categoryRepository.findRootCategories()
                .flatMap(category -> enrichSingleCategoryWithData(category, viewerContext));
    }

    @Override
    public Flux<CategoryResponse> getChildCategories(UUID parentId, ViewerContext viewerContext) {
        // Public - no permission check needed
        return categoryRepository.findChildCategories(parentId)
                .flatMap(category -> enrichSingleCategoryWithData(category, viewerContext));
    }

    // ==================== PRIVATE HELPERS ====================

    private Mono<Void> validateParentCategory(UUID parentCategoryId) {
        if (parentCategoryId == null) {
            return Mono.empty();
        }

        return categoryRepository.findById(parentCategoryId)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Parent category with id '" + parentCategoryId + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(parent -> {
                    if (parent.getParentCategoryId() != null) {
                        return Mono.error(new ApiException(
                                "Cannot create subcategory under a child category (only one level allowed)",
                                ErrorCode.INVALID_HIERARCHY));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateNoDuplicates(String name, String slug) {
        return Mono.zip(
                categoryRepository.existsByName(name),
                categoryRepository.existsBySlug(slug)
        ).flatMap(tuple -> {
            if (tuple.getT1()) {
                return Mono.error(new ApiException(
                        "Category name '" + name + "' already exists",
                        ErrorCode.DUPLICATE_CATEGORY_NAME));
            }
            if (tuple.getT2()) {
                return Mono.error(new ApiException(
                        "Category slug '" + slug + "' already exists",
                        ErrorCode.DUPLICATE_CATEGORY_SLUG));
            }
            return Mono.empty();
        });
    }

    private Mono<CategoryEntity> createAndSaveCategory(CreateCategoryRequest request, String slug) {
        CategoryEntity category = CategoryEntity.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .colorTheme(request.getColorTheme())
                .parentCategoryId(request.getParentCategoryId())
                .contentWarningType(request.getContentWarningType())
                .contentWarningCustomText(request.getContentWarningCustomText())
                .sortOrder(request.getSortOrder())
                .build();

        return categoryRepository.save(category);
    }


    private Mono<CategoryEntity> updateCategoryFields(CategoryEntity existing, UpdateCategoryRequest request, ViewerContext viewerContext) {
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getSlug() != null) existing.setSlug(request.getSlug());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getColorTheme() != null) existing.setColorTheme(request.getColorTheme());
        if (request.getParentCategoryId() != null) existing.setParentCategoryId(request.getParentCategoryId());
        if (request.getContentWarningType() != null) existing.setContentWarningType(request.getContentWarningType());
        if (request.getContentWarningCustomText() != null) existing.setContentWarningCustomText(request.getContentWarningCustomText());
        if (request.getSortOrder() != null) existing.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());

        if (request.getTagIds() != null && viewerContext != null) {
            return categoryTagService.syncCategoryTags(existing.getId(), request.getTagIds(), viewerContext)
                    .then(categoryRepository.save(existing));
        }

        return categoryRepository.save(existing);

    }

    private Mono<PaginatedResponse<CategoryResponse>> executeGetCategoriesQuery(
            int page,
            int size,
            UUID tagId,
            UUID parentCategoryId,
            Boolean isParent,
            Boolean isActive,
            Boolean isFocused,
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

        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();
        CategorySortField sortByField = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = sortByField.determineSortDirection(sortDirection);
        Boolean effectiveIsParent = (parentCategoryId != null && isParent != null) ? null : isParent;

        // User-friendly override: parent_category_id takes precedence
        if(effectiveIsParent == null){
            log.warn("Both parent_category_id and is_parent=true provided. Ignoring is_parent in favor of parent_category_id.");
        }

        Flux<CategoryEntity> categoriesFlux = categoryRepository.findAllCategoriesPaginated(
                viewerId,
                isAdmin, isModeratorOrAdmin, isVerified,
                tagId, parentCategoryId,
                effectiveIsParent, isActive, isFocused,
                effectiveSearch,
                sortByField.getValue(), effectiveSortDirection,
                size, offset
        );

        Mono<Long> totalCount = categoryRepository.countAllCategoriesWithFilters(
                viewerId,
                isAdmin, isModeratorOrAdmin, isVerified,
                tagId, parentCategoryId,
                effectiveIsParent, isActive, isFocused,
                effectiveSearch);

        return Mono.zip(
                categoriesFlux.collectList(),
                totalCount
        ).flatMap(tuple -> {
            List<CategoryEntity> categories = tuple.getT1();
            long total = tuple.getT2();

            if(categories.isEmpty()){
                return Mono.just(new PaginatedResponse<>(List.of(), page, size, 0L));
            }

            return enrichCategoriesWithBatchData(categories, viewerContext)
                    .map(enrichedCategories -> {

                        FilterMetadata<CategoryFilterDto> filters = buildCategoryFilters(enrichedCategories);
                        return new PaginatedResponse<>(enrichedCategories.responses, page, size, total, filters);
                    });
        });

    }

    private CategorySortField validateAndNormalizeSortBy(String sortBy) {
       return CategorySortField.fromString(sortBy);
    }


    /**
     * Enriches a single category with focus status and tags.
     * Uses individual queries since only one category is being fetched.
     * This is simpler and more readable than the batch approach for single items.
     */
    private Mono<CategoryResponse> enrichSingleCategoryWithData(CategoryEntity category, ViewerContext viewerContext){

        return Mono.zip(
                focusCategoryService.isCategoryFocused(category.getId(), viewerContext),
                categoryTagService.getTagsForCategory(category.getId()).collectList(),
                threadRepository.countActiveThreadsByCategory(category.getId())
        ).map(tuple -> {
            Boolean isFocused = tuple.getT1();
            List<CategoryTagResponse> tags = tuple.getT2();
            Long threadCount = tuple.getT3();

            return mapCategoryResponse(category, tags, isFocused, threadCount);
        });
    }

    /**
     * Enriches a list of categories with their tags using batch fetching.
     * Uses batch fetching to avoid N+1 queries.
     */
    private Mono<EnrichedCategoryData> enrichCategoriesWithBatchData(
        List<CategoryEntity> categories,
        ViewerContext viewerContext
    ){
        if(categories.isEmpty()){
            return Mono.just(new EnrichedCategoryData(
                    List.of(),
                    List.of(),
                    List.of()
            ));
        }

        UUID currentUserId = viewerContext != null? UUID.fromString(viewerContext.getUserId()): null;

        List<UUID> categoryIds = categories.stream()
                .map(CategoryEntity::getId)
                .toList();

        // Batch fetch category tags for all categories
        Mono<List<CategoryTagWithCategoryId>> flatTagsListMono = categoryTagRepository
                .findTagsByCategoryId(categoryIds)
                .collectList();

        // Batch fetch focus status for all categories (if user authenticated)
        Mono<Set<UUID>> focusedSet = Mono.just(Set.of());
        if(currentUserId != null){
            focusedSet = focusCategoryRepository.findFocusCategoryIds(currentUserId, categoryIds)
                    .collect(Collectors.toSet())
                    .defaultIfEmpty(Set.of());
        }

        // Batch fetch thread counts for categories
        Mono<Map<UUID, Long>> threadCountsMap = threadRepository.findThreadCountsByCategoryIds(categoryIds)
                .collectMap(ThreadCountRecord::category_id, ThreadCountRecord::count)
                .defaultIfEmpty(new HashMap<>());

        return Mono.zip(flatTagsListMono, focusedSet, threadCountsMap)
                .map(tuple -> {
                    List<CategoryTagWithCategoryId> flatTagsList = tuple.getT1();
                    Set<UUID> focusIds = tuple.getT2();
                    Map<UUID, Long> threadCounts = tuple.getT3();

                    Map<UUID, List<CategoryTagResponse>> tagsByCategory = flatTagsList.stream()
                            .collect(Collectors.groupingBy(
                                    CategoryTagWithCategoryId::category_id,
                                    Collectors.mapping(this::mapToCategoryTag,
                                            Collectors.toList())
                            ));


                    List<CategoryResponse> responses = categories.stream()
                        .map(category -> {
                            List<CategoryTagResponse> tags = tagsByCategory.getOrDefault(
                                    category.getId(), List.of()
                            );
                            boolean isFocused = focusIds.contains(category.getId());
                            Long threadCount = threadCounts.getOrDefault(category.getId(), 0L);
                            return mapCategoryResponse(category, tags, isFocused, threadCount);
                        })
                        .toList();

                    return new EnrichedCategoryData(
                            responses,
                            categories,
                            flatTagsList
                    );

                });
    }

    private CategoryResponse mapCategoryResponse(
            CategoryEntity category,
            List<CategoryTagResponse> tags,
            Boolean isFocused,
            Long threadCount) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .colorTheme(category.getColorTheme())
                .parentCategoryId(category.getParentCategoryId())
                .contentWarningType(category.getContentWarningType())
                .contentWarningCustomText(category.getContentWarningCustomText())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .isParent(category.isParent())
                .isChild(category.isChild())
                .isFocused(isFocused)
                .tags(tags)
                .threadCount(threadCount)
                .build();
    }

    private record EnrichedCategoryData(
            List<CategoryResponse> responses,
            List<CategoryEntity> categories,
            List<CategoryTagWithCategoryId> flatTags
    ) {}

    private CategoryTagResponse mapToCategoryTag(CategoryTagWithCategoryId record){
        return CategoryTagResponse.builder()
                .id(record.id())
                .name(record.name())
                .slug(record.slug())
                .description(record.description())
                .createdBy(record.created_by())
                .createdAt(record.created_at())
                .updatedAt(record.updated_at())
                .build();
    }

    private FilterMetadata<CategoryFilterDto> buildCategoryFilters(EnrichedCategoryData data){
        // Build tag options
        Map<UUID, List<CategoryTagWithCategoryId>> tagsByCategory = data.flatTags().stream()
                .collect(Collectors.groupingBy(CategoryTagWithCategoryId::category_id));

        // Count categories per tag
        Map<UUID, Set<UUID>> tagToCategories = new HashMap<>();
        for (CategoryEntity category : data.categories()){
            UUID categoryId = category.getId();
            List<CategoryTagWithCategoryId> tags = tagsByCategory.getOrDefault(categoryId, List.of());

            for(CategoryTagWithCategoryId tag : tags){
                tagToCategories.computeIfAbsent(tag.id(), k -> new HashSet<>()).add(categoryId);
            }
        }

        // Build FilterOption from unique tags
        List<FilterOption> tagOptions = data.flatTags.stream()
                .collect(Collectors.toMap(
                        CategoryTagWithCategoryId::id,
                        Function.identity(),
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .map(tag -> new FilterOption(
                        tag.id(),
                        tag.name(),
                        tag.slug(),
                        (long) tagToCategories.getOrDefault(tag.id(), Set.of()).size()
                ))
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();

        // Build parent options
        List<FilterOption> parentOptions = data.categories().stream()
                .filter(CategoryEntity::isParent)
                .map(parent -> {
                    long count = data.categories().stream()
                            .filter(category -> parent.getId().equals(category.getParentCategoryId()))
                            .count();

                    return new FilterOption(
                            parent.getId(),
                            parent.getName(),
                            parent.getSlug(),
                            count
                    );
                })
                .sorted(Comparator.comparing(FilterOption::getLabel))
                .toList();

        CategoryFilterDto categoryFilters = CategoryFilterDto.builder()
                .tags(tagOptions)
                .parentCategories(parentOptions)
                .build();

        return FilterMetadata.<CategoryFilterDto>builder()
                .filters(categoryFilters)
                .sortOptions(getCategorySortOptions())
                .build();

    }

    private List<SortOption> getCategorySortOptions() {
        return Arrays.stream(
                CategorySortField.values())
                .map(CategorySortField::toSortOption)
                .toList();
    }

}