package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ModerationAction;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumCategoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumCategoryTagRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumThreadRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumCategoryService;
import com.mentalhealthforum.mentalhealthforum_backend.utils.NormalizeUtils;
import com.mentalhealthforum.mentalhealthforum_backend.utils.SlugsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
public class ForumCategoryServiceImpl implements ForumCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ForumCategoryServiceImpl.class);

    private final TransactionalOperator transactionalOperator;
    private final ForumCategoryRepository forumCategoryRepository;
    private final ForumCategoryTagRepository forumCategoryTagRepository;
    private final ForumThreadRepository forumThreadRepository;

    public ForumCategoryServiceImpl(TransactionalOperator transactionalOperator,
                                    ForumCategoryRepository forumCategoryRepository,
                                    ForumCategoryTagRepository forumCategoryTagRepository,
                                    ForumThreadRepository forumThreadRepository) {
        this.transactionalOperator = transactionalOperator;
        this.forumCategoryRepository = forumCategoryRepository;
        this.forumCategoryTagRepository = forumCategoryTagRepository;
        this.forumThreadRepository = forumThreadRepository;
    }

    // ==================== SLUG GENERATION ====================

    @Override
    public Mono<SlugGenerationResponse> generateSlug(String name, UUID excludeCategoryId) {

        // Slug generation is a utility, no permission check needed (admin only endpoint already)

        String baseSlug = SlugsUtil.generateSlug(name);

        if (baseSlug.isEmpty()) {
            return Mono.error(new ApiException(
                    "Could not generate slug from provided name",
                    ErrorCode.INVALID_SLUG_GENERATION));
        }

        Function<String, Mono<Boolean>> existsCheck = slug -> excludeCategoryId == null
                ? forumCategoryRepository.existsBySlug(slug)
                : forumCategoryRepository.existsBySlugAndIdNot(slug, excludeCategoryId);

        return SlugsUtil.generateUniqueSlugReactive(name, existsCheck)
                .map(uniqueSlug -> new SlugGenerationResponse(uniqueSlug, uniqueSlug.equals(baseSlug)));
    }

    // ==================== CATEGORY CRUD ====================

    @Override
    public Mono<ForumCategoryResponse> createCategory(CreateForumCategoryRequest request, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_CREATED.checkPermission(viewerContext)
                .then(doCreateCategory(request))
                .flatMap(this::mapToResponseWithTags);
    }

    private Mono<ForumCategoryEntity> doCreateCategory(CreateForumCategoryRequest request) {

        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = SlugsUtil.generateSlug(request.getName());
        }
        String finalSlug = slug;

        return validateParentCategory(request.getParentCategoryId())
                .then(validateNoDuplicates(request.getName(), finalSlug))
                .then(createAndSaveCategory(request, finalSlug))
                .flatMap(savedCategory -> addTagsToCategory(savedCategory, request.getTags())
                        .thenReturn(savedCategory)
                )
                .as(transactionalOperator::transactional);

    }

    @Override
    public Mono<ForumCategoryResponse> updateCategory(UUID id, UpdateForumCategoryRequest request, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_UPDATED.checkPermission(viewerContext)
                .then(doUpdateCategory(id, request))
                .flatMap(this::mapToResponseWithTags);
    }

    private Mono<ForumCategoryEntity> doUpdateCategory(UUID id, UpdateForumCategoryRequest request) {
        return forumCategoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '" + id + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(existingCategory -> {
                    String newSlug = request.getSlug();
                    if (newSlug != null && !newSlug.equals(existingCategory.getSlug())) {
                        return forumCategoryRepository.existsBySlugAndIdNot(newSlug, id)
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new ApiException(
                                                "Slug '" + newSlug + "' already exists",
                                                ErrorCode.DUPLICATE_CATEGORY_SLUG));
                                    }
                                    return updateCategoryFields(existingCategory, request);
                                });
                    }
                    return updateCategoryFields(existingCategory, request);
                });
    }

    @Override
    public Mono<Void> softDeleteCategory(UUID id, ViewerContext viewerContext) {
       return ModerationAction.CATEGORY_SOFT_DELETED.checkPermission(viewerContext)
                .then(doSoftDeleteCategory(id));

    }

    private Mono<Void> doSoftDeleteCategory(UUID id) {
        return forumCategoryRepository.findById(id)
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
                    return forumCategoryRepository.save(category).then();
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ForumCategoryResponse> reactivateCategory(UUID id, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_REACTIVATED.checkPermission(viewerContext)
                .then(doReactivateCategory(id))
                .flatMap(this::mapToResponseWithTags);
    }

    private Mono<ForumCategoryEntity> doReactivateCategory(UUID id) {
        return forumCategoryRepository.findById(id)
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

                        return forumCategoryRepository.findById(category.getParentCategoryId())
                                .flatMap(parent -> {
                                    if (!parent.getIsActive()) {
                                        return Mono.error(new ApiException(
                                                "Cannot reactivate: Parent category is inactive",
                                                ErrorCode.INVALID_HIERARCHY));
                                    }

                                    category.setIsActive(true);
                                    return forumCategoryRepository.save(category);
                                });
                    }
                    category.setIsActive(true);
                    return forumCategoryRepository.save(category);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> purgeCategory(UUID id, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_PURGED.checkPermission(viewerContext)
                .then(doPurgeCategory(id));
    }

    private Mono<Void> doPurgeCategory(UUID id) {
        return forumCategoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category -> {
                    // Check if category has active children (can't purge if it has active children)
                    return forumCategoryRepository.findChildCategories(id)
                            .filter(ForumCategoryEntity::getIsActive)
                            .hasElements()
                            .flatMap(hasActiveElements -> {
                                if (hasActiveElements) {
                                    return Mono.error(new ApiException(
                                            "Cannot purge category with active child categories",
                                            ErrorCode.INVALID_HIERARCHY));
                                }

                                return forumThreadRepository.existsByCategoryId(id)
                                        .flatMap(hasThreads -> {
                                            if(hasThreads){
                                                return Mono.error(new ApiException(
                                                        "Cannot purge category with existing threads. Delete or move threads first.",
                                                        ErrorCode.VALIDATION_FAILED
                                                ));
                                            }

                                            // Delete associated tags first, then delete category
                                            return forumCategoryTagRepository.deleteByCategoryId(id)
                                                    .then(forumCategoryRepository.delete(category));
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

        return forumCategoryRepository.findInactiveCategoriesOlderThan(cutoffDate)
                .flatMap(category -> {
                    // Delete tags first, then category
                    return forumCategoryTagRepository.deleteByCategoryId(category.getId())
                            .then(forumCategoryRepository.delete(category));
                })
                .then()
                .doOnSuccess(v -> log.info("Purged inactive categories older than {} days", daysOld))
                .doOnError(e -> log.error("Error purging old categories: {}", e.getMessage()));
    }

    // ==================== QUERIES (with permission checks where needed) ====================

    @Override
    public Mono<ForumCategoryResponse> getCategoryById(UUID id) {
        // Public - no permission check needed
        return forumCategoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '" + id + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(this::mapToResponseWithTags);
    }

    @Override
    public Mono<ForumCategoryResponse> getCategoryBySlug(String slug) {
        // Public - no permission check needed
        return forumCategoryRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with slug '" + slug + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(this::mapToResponseWithTags);
    }

    @Override
    public Mono<PaginatedResponse<ForumCategoryResponse>> getActiveCategories(
            int page,
            int size,
            String tagName,
            UUID parentCategoryId,
            Boolean isParent,
            String search,
            String sortBy,
            String sortDirection
    ) {
        // Public - no permission check needed
        return executeGetCategoriesQuery(page, size, tagName, parentCategoryId, isParent, search, true, sortBy, sortDirection);
    }

    @Override
    public Mono<PaginatedResponse<ForumCategoryResponse>> getAllCategories(
            int page,
            int size,
            String tagName,
            UUID parentCategoryId,
            Boolean isParent,
            String search,
            Boolean isActive,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    ) {
        return ModerationAction.CATEGORY_VIEW_INACTIVE.checkPermission(viewerContext)
                .then(executeGetCategoriesQuery(page, size, tagName, parentCategoryId, isParent, search, isActive, sortBy, sortDirection));

    }

    @Override
    public Mono<Long> getInactiveCount(ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_VIEW_INACTIVE.checkPermission(viewerContext)
                .then(forumCategoryRepository.countByIsActiveFalse());
    }

    // ==================== HIERARCHY ====================

    @Override
    public Flux<ForumCategoryHierarchyDto> getCategoryHierarchy() {
        // Public - no permission check needed
        return forumCategoryRepository.findRootCategories()
                .flatMap(root -> Mono.zip(
                        mapToResponseWithTags(root),
                        forumCategoryRepository.findChildCategories(root.getId())
                                .flatMap(this::mapToResponseWithTags)
                                .collectList(),
                        forumCategoryTagRepository.findByCategoryId(root.getId())
                                .map(this::mapTagToResponse)
                                .collectList()
                ))
                .map(tuple -> new ForumCategoryHierarchyDto(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ));
    }

    @Override
    public Flux<ForumCategoryResponse> getRootCategories() {
        // Public - no permission check needed
        return forumCategoryRepository.findRootCategories()
                .flatMap(this::mapToResponseWithTags);
    }

    @Override
    public Flux<ForumCategoryResponse> getChildCategories(UUID parentId) {
        // Public - no permission check needed
        return forumCategoryRepository.findChildCategories(parentId)
                .flatMap(this::mapToResponseWithTags);
    }

    // ==================== TAG OPERATIONS ====================

    @Override
    public Flux<ForumCategoryTagResponse> getTags(UUID categoryId) {
        // Public - no permission check needed (tags are visible to everyone)
        return forumCategoryTagRepository.findByCategoryId(categoryId)
                .map(this::mapTagToResponse);
    }

    @Override
    public Mono<ForumCategoryTagResponse> addTag(UUID categoryId, ForumCategoryTagRequest request, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_TAG_ADDED.checkPermission(viewerContext)
                        .then(doAddTag(categoryId, request))
                .map(this::mapTagToResponse);
    }

    private Mono<ForumCategoryTagEntity> doAddTag(UUID categoryId, ForumCategoryTagRequest request) {
        return  forumCategoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '" + categoryId + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category ->
                        forumCategoryTagRepository.existsByCategoryIdAndTagName(categoryId, request.name())
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new ApiException(
                                                "Tag '" + request.name() + "' already exists",
                                                ErrorCode.TAG_ALREADY_EXISTS));
                                    }
                                    return saveTag(categoryId, request);
                                })
                )
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ForumCategoryTagResponse> updateTagDescription(UUID categoryId, String tagName, String description, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_TAG_UPDATED.checkPermission(viewerContext)
                .then(doUpdateTagDescription(categoryId, tagName, description))
                .map(this::mapTagToResponse);
    }

    private Mono<ForumCategoryTagEntity> doUpdateTagDescription(UUID categoryId, String tagName, String description) {
        return forumCategoryTagRepository.findByCategoryIdAndTagName(categoryId, tagName)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Tag '" + tagName + "' not found in category '" + categoryId + "'",
                        ErrorCode.TAG_NOT_FOUND)))
                .flatMap(tag -> {
                    tag.setTagDescription(description);
                    return forumCategoryTagRepository.save(tag);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> removeTag(UUID categoryId, String tagName, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_TAG_REMOVED.checkPermission(viewerContext)
                .then(doRemoveTag(categoryId, tagName));
    }

    private Mono<Void> doRemoveTag(UUID categoryId, String tagName) {
        return forumCategoryTagRepository.findByCategoryIdAndTagName(categoryId, tagName)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Tag '" + tagName + "' not found in category '" + categoryId + "'",
                        ErrorCode.TAG_NOT_FOUND)))
                .flatMap(tag -> forumCategoryTagRepository.deleteByCategoryIdAndTagName(categoryId, tagName))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> replaceTags(UUID categoryId, List<ForumCategoryTagRequest> tags, ViewerContext viewerContext) {
        return ModerationAction.CATEGORY_TAG_REPLACED.checkPermission(viewerContext)
                .then(doReplaceTags(categoryId, tags));

    }

    private Mono<Void> doReplaceTags(UUID categoryId, List<ForumCategoryTagRequest> tags) {
        return forumCategoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '" + categoryId + "' not found",
                        ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(category ->
                        forumCategoryTagRepository.deleteByCategoryId(categoryId)
                                .thenMany(Flux.fromIterable(tags))
                                .flatMap(tagRequest -> saveTag(categoryId, tagRequest))
                                .then()
                )
                .as(transactionalOperator::transactional);
    }

    // ==================== PRIVATE HELPERS ====================

    private Mono<Void> validateParentCategory(UUID parentCategoryId) {
        if (parentCategoryId == null) {
            return Mono.empty();
        }

        return forumCategoryRepository.findById(parentCategoryId)
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
                forumCategoryRepository.existsByName(name),
                forumCategoryRepository.existsBySlug(slug)
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

    private Mono<ForumCategoryEntity> createAndSaveCategory(CreateForumCategoryRequest request, String slug) {
        ForumCategoryEntity category = ForumCategoryEntity.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .colorTheme(request.getColorTheme())
                .parentCategoryId(request.getParentCategoryId())
                .contentWarningType(request.getContentWarningType())
                .contentWarningCustomText(request.getContentWarningCustomText())
                .sortOrder(request.getSortOrder())
                .build();

        return forumCategoryRepository.save(category);
    }

    private Mono<ForumCategoryTagEntity> saveTag(UUID categoryId, ForumCategoryTagRequest request) {
        String normalizedTagName = NormalizeUtils.normalizeTag(request.name());

        if (normalizedTagName.isEmpty()) {
            return Mono.error(new ApiException("Tag name contains no valid characters", ErrorCode.VALIDATION_FAILED));
        }

        ForumCategoryTagEntity tag = ForumCategoryTagEntity.builder()
                .categoryId(categoryId)
                .tagName(normalizedTagName)
                .tagDescription(request.description())
                .build();
        return forumCategoryTagRepository.save(tag);
    }

    private Mono<Void> addTagsToCategory(ForumCategoryEntity category, List<ForumCategoryTagRequest> tags) {
        if (tags == null || tags.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(tags)
                .flatMap(tagRequest -> saveTag(category.getId(), tagRequest))
                .then();
    }

    private Mono<ForumCategoryEntity> updateCategoryFields(ForumCategoryEntity existing, UpdateForumCategoryRequest request) {
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getSlug() != null) existing.setSlug(request.getSlug());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getColorTheme() != null) existing.setColorTheme(request.getColorTheme());
        if (request.getParentCategoryId() != null) existing.setParentCategoryId(request.getParentCategoryId());
        if (request.getContentWarningType() != null) existing.setContentWarningType(request.getContentWarningType());
        if (request.getContentWarningCustomText() != null) existing.setContentWarningCustomText(request.getContentWarningCustomText());
        if (request.getSortOrder() != null) existing.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());

        if (request.getTags() != null) {
            return forumCategoryTagRepository.deleteByCategoryId(existing.getId())
                    .then(addTagsToCategory(existing, request.getTags()))
                    .then(forumCategoryRepository.save(existing));
        }

        return forumCategoryRepository.save(existing);
    }

    private Mono<PaginatedResponse<ForumCategoryResponse>> executeGetCategoriesQuery(
            int page,
            int size,
            String tagName,
            UUID parentCategoryId,
            Boolean isParent,
            String search,
            Boolean isActive,
            String sortBy,
            String sortDirection
    ){
        if(page < 0 || size <= 0){
            throw new InvalidPaginationException();
        }

        int offset = page * size;
        String effectiveTag = (tagName == null || tagName.isBlank()) ? null : tagName.trim();
        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();
        String effectiveSortBy = validateAndNormalizeSortBy(sortBy);
        String effectiveSortDirection = determineSortDirection(sortDirection, effectiveSortBy);

        // User-friendly override: parent_category_id takes precedence
        if(parentCategoryId != null && isParent != null){
            log.warn("Both parent_category_id and is_parent=true provided. Ignoring is_parent in favor of parent_category_id.");
            isParent = null;
        }

        return forumCategoryRepository.findAllCategoriesPaginated(
                effectiveTag, parentCategoryId, isParent, effectiveSearch, isActive, effectiveSortBy, effectiveSortDirection, size, offset
        )
                .flatMap(this::mapToResponseWithTags)
                .collectList()
                .zipWith(forumCategoryRepository.countAllCategoriesWithFilters(effectiveTag, parentCategoryId, isParent, effectiveSearch, isActive))
                .map(tuple -> new PaginatedResponse<>(tuple.getT1(), page, size, tuple.getT2()));

    }

    private String validateAndNormalizeSortBy(String sortBy) {
        Set<String> allowedFields = Set.of("sort_order", "name", "created_at");
        if(sortBy == null || !allowedFields.contains(sortBy)){
            return "sort_order";
        }
        return sortBy;
    }

    private String determineSortDirection(String sortDirection, String effectiveSortBy) {
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        }
        return "ASC";
    }

    private Mono<ForumCategoryResponse> mapToResponseWithTags(ForumCategoryEntity category){
        return  forumCategoryTagRepository.findByCategoryId(category.getId())
                    .map(this::mapTagToResponse)
                        .collectList()
                        .map(tags -> ForumCategoryResponse.builder()
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
                                .tags(tags)
                                .build());

    }

    private ForumCategoryTagResponse mapTagToResponse(ForumCategoryTagEntity tag) {
        return ForumCategoryTagResponse.builder()
                .id(tag.getId())
                .name(tag.getTagName())
                .description(tag.getTagDescription())
                .build();
    }
}