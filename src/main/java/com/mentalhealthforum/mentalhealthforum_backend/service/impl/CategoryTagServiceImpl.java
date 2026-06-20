package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.UserDetails;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ModerationAction;
import com.mentalhealthforum.mentalhealthforum_backend.enums.listings.TagSortField;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidPaginationException;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUserEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryTagAssignmentEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.AppUserRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.CategoryTagAssignmentRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.CategoryTagRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.CategoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.AppUserService;
import com.mentalhealthforum.mentalhealthforum_backend.service.CategoryTagService;
import com.mentalhealthforum.mentalhealthforum_backend.utils.NormalizeUtils;
import com.mentalhealthforum.mentalhealthforum_backend.utils.SlugsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;


@Service
public class CategoryTagServiceImpl implements CategoryTagService {

    private static final Logger log = LoggerFactory.getLogger(CategoryTagServiceImpl.class);

    // ==================== CONSTANTS ====================
    private static final int MAX_TAGS_PER_CATEGORY = 3;

    private final TransactionalOperator transactionalOperator;
    private final CategoryTagRepository categoryTagRepository;
    private final CategoryTagAssignmentRepository categoryTagAssignmentRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;
    private final AppUserService appUserService;

    public CategoryTagServiceImpl(
            TransactionalOperator transactionalOperator,
            CategoryTagRepository categoryTagRepository,
            CategoryTagAssignmentRepository categoryTagAssignmentRepository,
            CategoryRepository categoryRepository,
            AppUserRepository appUserRepository,
            AppUserService appUserService) {
        this.transactionalOperator = transactionalOperator;
        this.categoryTagRepository = categoryTagRepository;
        this.categoryTagAssignmentRepository = categoryTagAssignmentRepository;
        this.categoryRepository = categoryRepository;
        this.appUserRepository = appUserRepository;
        this.appUserService = appUserService;
    }

    @Override
    public Mono<CategoryTagResponse> createTag(CategoryTagRequest request, ViewerContext viewerContext){

        return ModerationAction.CATEGORY_TAG_CREATED.checkPermission(viewerContext)
                .then(doCreateTag(request, viewerContext));

    }

    private Mono<CategoryTagResponse> doCreateTag(CategoryTagRequest request, ViewerContext viewerContext) {
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return validateAndNormalizeTag(request.name())
                .flatMap(validated -> validateTagNotExists(validated.name, validated.slug)
                        .then(createTagEntity(validated.name, validated.slug, request.description(), userId)))
                .flatMap(this::enrichSingleTagWithData)
                .as(transactionalOperator::transactional);
    }


    @Override
    public Mono<CategoryTagResponse> updateTag(UUID tagId, CategoryTagRequest request, ViewerContext viewerContext){

        return ModerationAction.CATEGORY_TAG_UPDATED.checkPermission(viewerContext)
                .then(doUpdateTag(tagId, request));

    }

    private Mono<CategoryTagResponse> doUpdateTag(UUID tagId, CategoryTagRequest request) {
        return categoryTagRepository.findById(tagId)
                .switchIfEmpty(Mono.error(new ApiException("Tag not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(existingTag -> validateAndNormalizeTag(request.name())
                        .flatMap(validated -> {
                            String normalizedName = validated.name;
                            String newSlug = validated.slug;

                            if(!normalizedName.equals(existingTag.getName())){
                                return validateTagNotExists(normalizedName, newSlug)
                                        .then(updateTagEntity(existingTag, normalizedName, newSlug, request.description()));
                            }

                            return updateTagEntity(existingTag, existingTag.getName(), existingTag.getSlug(), request.description());
                        }))
                .flatMap(this::enrichSingleTagWithData)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> deleteTags(DeleteTagRequest request, ViewerContext viewerContext){

        return ModerationAction.CATEGORY_TAG_DELETED.checkPermission(viewerContext)
                .then(doDeleteTag(request.tagIds()));

    }

    private Mono<Void> doDeleteTag(List<UUID> tagIds) {

        if (tagIds == null || tagIds.isEmpty()) {
            return Mono.empty();
        }

        if (tagIds.size() > 20) {
            return Mono.error(new ApiException("Cannot delete more than 20 tags at once", ErrorCode.VALIDATION_FAILED));
        }

        return Flux.fromIterable(tagIds)
                .flatMap(tagId -> categoryTagRepository.findById(tagId)
                        .switchIfEmpty(Mono.error(new ApiException("Tag not found", ErrorCode.RESOURCE_NOT_FOUND)))
                )
                .flatMap(categoryTagRepository::delete)
                .then()
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<CategoryTagResponse> getTagById(UUID tagId){
        return categoryTagRepository.findById(tagId)
                .switchIfEmpty(Mono.error(new ApiException("Tag not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(this::enrichSingleTagWithData);
    }

    @Override
    public Mono<CategoryTagResponse> getTagByName(String name){
        String normalizedName = NormalizeUtils.normalizeTag(name);
        return categoryTagRepository.findByName(name)
                .switchIfEmpty(Mono.error(new ApiException("Tag not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(this::enrichSingleTagWithData);
    }

    @Override
    public Mono<CategoryTagResponse> getTagBySlug(String slug){
        return categoryTagRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ApiException("Tag not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .flatMap(this::enrichSingleTagWithData);
    }

    @Override
    public Mono<PaginatedResponse<CategoryTagResponse>> getAllTags(int page, int size, String search, String sortBy, String sortDirection){
        if(page < 0 || size <= 0){
            throw new InvalidPaginationException();
        }

        int offset = page * size;
        String effectiveSearch = (search == null || search.isBlank()) ? null : search.trim();

        TagSortField sortByField = validateAndNormalizeTagSortBy(sortBy);
        String effectiveSortDirection = determineSortDirection(sortDirection, sortByField);

        return categoryTagRepository.searchTags(
                    effectiveSearch,
                    sortByField.getValue(), effectiveSortDirection,
                    size, offset
                )
                .collectList()
                .flatMap(tags -> {
                    if(tags.isEmpty()){
                        return Mono.just(new PaginatedResponse<>(List.of(), page, size, 0L));
                    }
                    return enrichTagsWithBatchData(tags)
                            .zipWith(categoryTagRepository.countSearchTags(effectiveSearch))
                            .map(tuple -> new PaginatedResponse<>(tuple.getT1(), page, size, tuple.getT2()));

                });
    }

    // ==================== CATEGORY-TAG ASSIGNMENTS ====================

    @Override
    public Mono<CategoryTagAssignmentResponse> assignTagToCategory(UUID categoryId, UUID tagId, ViewerContext viewerContext){

        return ModerationAction.CATEGORY_TAG_ASSIGNED.checkPermission(viewerContext)
                .then(doAssignTagToCategory(categoryId, tagId, viewerContext));

    }

    private Mono<CategoryTagAssignmentResponse> doAssignTagToCategory(UUID categoryId, UUID tagId, ViewerContext viewerContext) {
        UUID userId = UUID.fromString(viewerContext.getUserId());

        return validateCategoryExists(categoryId)
                .then(validateTagExists(tagId))
                .then(checkNotAlreadyAssigned(categoryId, tagId))
                .then(validateTagLimit(categoryId))
                .then(createAssignment(categoryId, tagId, userId))
                .flatMap(this::mapAssignmentToResponse)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Flux<CategoryTagAssignmentResponse> assignTagToCategories(AssignTagToCategories request, UUID tagId, ViewerContext viewerContext){

        return ModerationAction.CATEGORY_TAG_ASSIGNED.checkPermission(viewerContext)
                .thenMany(doAssignTagToCategories(request.categoryIds(), tagId, viewerContext));
    }

    private Flux<CategoryTagAssignmentResponse> doAssignTagToCategories(List<UUID> categoryIds, UUID tagId, ViewerContext viewerContext) {
        UUID userId = UUID.fromString(viewerContext.getUserId());

        if(categoryIds == null || categoryIds.isEmpty()){
            return Flux.empty();
        }

        if(categoryIds.size() > 20){
            return Flux.error(new ApiException("Cannot assign to more than 20 categories at once", ErrorCode.VALIDATION_FAILED));
        }

        // Validate tag exists
        return validateTagExists(tagId)
                .thenMany(Flux.fromIterable(categoryIds))
                .concatMap(categoryId -> {

                    // For each category, check if tag already assigned
                    return validateCategoryExists(categoryId)
                            .then(checkNotAlreadyAssigned(categoryId, tagId))
                            .then(validateTagLimit(categoryId))
                            .then(createAssignment(categoryId, tagId, userId))
                            .flatMap(this::mapAssignmentToResponse);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> removeTagFromCategory(UUID categoryId, UUID tagId, ViewerContext viewerContext){

        return ModerationAction.CATEGORY_TAG_UNASSIGNED.checkPermission(viewerContext)
                .then(doRemoveTagFromCategory(categoryId, tagId));

    }

    private Mono<Void> doRemoveTagFromCategory(UUID categoryId, UUID tagId) {
        return Mono.zip(
                        validateCategoryExists(categoryId),
                        validateTagExists(tagId),
                        validateAssignmentExists(categoryId, tagId)
                ).then(categoryTagAssignmentRepository.deleteByCategoryIdAndTagId(categoryId, tagId))
                .as(transactionalOperator::transactional);
    }


    @Override
    public Flux<CategoryTagAssignmentResponse> removeTagFromCategories(RemoveTagFromCategories request, UUID tagId, ViewerContext viewerContext){

        return ModerationAction.CATEGORY_TAG_UNASSIGNED.checkPermission(viewerContext)
                .thenMany(doRemoveTagFromCategories(request.categoryIds(), tagId, viewerContext));
    }

    private Flux<CategoryTagAssignmentResponse> doRemoveTagFromCategories(List<UUID> categoryIds, UUID tagId, ViewerContext viewerContext) {

        if(categoryIds == null || categoryIds.isEmpty()){
            return Flux.empty();
        }

        if(categoryIds.size() > 20){
            return Flux.error(new ApiException("Cannot assign to more than 20 categories at once", ErrorCode.VALIDATION_FAILED));
        }

        // Validate tag exists
        return validateTagExists(tagId)
                .thenMany(Flux.fromIterable(categoryIds))
                .flatMap(categoryId -> {

                    // For each category, check if tag already assigned
                    return validateCategoryExists(categoryId)
                            .then(validateAssignmentExists(categoryId, tagId))
                            .thenMany(categoryTagAssignmentRepository.findByCategoryIdAndTagId(categoryId, tagId))
                            .flatMap(this::mapAssignmentToResponse)
                            .flatMap(response -> {
                                return categoryTagAssignmentRepository.deleteByCategoryIdAndTagId(categoryId, tagId)
                                        .then(Mono.just(response));
                            });
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> deleteAllTagAssignmentsForCategory(UUID categoryId, ViewerContext viewerContext){
        return ModerationAction.CATEGORY_TAG_UNASSIGNED.checkPermission(viewerContext)
                .then(categoryTagAssignmentRepository.deleteByCategoryId(categoryId))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Flux<CategoryTagResponse> getTagsForCategory(UUID categoryId){
        return categoryTagRepository.findByCategoryId(categoryId)
                .flatMap(this::enrichSingleTagWithData);
    }

    @Override
    public Flux<CategoryTagAssignmentResponse> getAssignmentsForCategory(UUID categoryId){
        return categoryTagAssignmentRepository.findByCategoryId(categoryId)
                .flatMap(this::mapAssignmentToResponse);
    }

    @Override
    public Mono<Void> addTagsToCategory(CategoryEntity category, List<UUID> tagIds, ViewerContext viewerContext) {

        return validateTagIds(tagIds)
                .then(Flux.fromIterable(tagIds)
                        .flatMap(tagId -> assignTagToCategory(category.getId(), tagId, viewerContext))
                        .then());
    }

    @Override
    public Mono<Void> syncCategoryTags(UUID categoryId, List<UUID> newTagIds, ViewerContext viewerContext) {

        // Get current tags for this category
        return validateTagIds(newTagIds)
                .then( getTagsForCategory(categoryId)
                        .collectList()
                        .flatMap(existingTags -> {
                            // Tags to remove: IDs not in new list
                            List<CategoryTagResponse> toRemove = existingTags.stream()
                                    .filter(existing -> !newTagIds.contains(existing.getId()))
                                    .toList();

                            List<UUID> toAdd = newTagIds.stream()
                                    .filter(newId -> existingTags.stream()
                                            .noneMatch(existing -> existing.getId().equals(newId)))
                                    .toList();

                            // Validate all tag IDs exist
                            return Flux.fromIterable(newTagIds)
                                    .flatMap(this::validateTagExists)
                                    .then()
                                    .thenMany(Flux.fromIterable(toRemove))
                                    .flatMap(tag -> removeTagFromCategory(categoryId, tag.getId(), viewerContext))
                                    .then()
                                    .thenMany(Flux.fromIterable(toAdd))
                                    .flatMap(tagId -> assignTagToCategory(categoryId, tagId, viewerContext))
                                    .then();
                        }));
    }


    // ==================== PRIVATE HELPERS ====================

    private Mono<ValidatedTagData> validateAndNormalizeTag(String name){
        // Normalize tag name
        String normalizedName = NormalizeUtils.normalizeTag(name);
        if(normalizedName.isEmpty()){
            return Mono.error(new ApiException(
                    "Tag name contains no valid characters",
                    ErrorCode.VALIDATION_FAILED
            ));
        }

        // Generate slug from normalized name
        String baseSlug = SlugsUtil.generateSlug(normalizedName);
        if(baseSlug.isEmpty()){
            return Mono.error(new ApiException(
                    "Could not generate slug from tag name",
                    ErrorCode.INVALID_SLUG_GENERATION
            ));
        }

        return generateUniqueSlug(baseSlug)
                .map(uniqueSlug -> new ValidatedTagData(normalizedName, uniqueSlug));
    }

    private record ValidatedTagData(String name, String slug){};

    private Mono<String> generateUniqueSlug(String baseSlug){
        Function<String, Mono<Boolean>> existsCheck = categoryTagRepository::existsBySlug;
        return SlugsUtil.generateUniqueSlugReactive(baseSlug, existsCheck);
    }


    private Mono<Void> validateTagNotExists(String name, String slug) {
        return Mono.zip(
                categoryTagRepository.existsByName(name),
                categoryTagRepository.existsBySlug(slug)
        ).flatMap(tuple -> {
            if(tuple.getT1()){
                return Mono.error(new ApiException(
                        "Tag with name '" + name + "' already exists",
                        ErrorCode.VALIDATION_FAILED
                ));
            }
            if(tuple.getT2()){
                return Mono.error(new ApiException(
                        "Tag with '" + slug + "' already exists",
                        ErrorCode.VALIDATION_FAILED
                ));
            }
            return Mono.empty();
        });
    }

    private Mono<CategoryTagEntity> createTagEntity(String normalizedName, String slug, String description, UUID userId){
        CategoryTagEntity tag = CategoryTagEntity.builder()
                .name(normalizedName)
                .slug(slug)
                .description(description)
                .createdBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return categoryTagRepository.save(tag);
    }

    private Mono<CategoryTagEntity> updateTagEntity(CategoryTagEntity existingTag, String name, String slug, String description){
        existingTag.setName(name);
        existingTag.setSlug(slug);
        existingTag.setDescription(description);
        existingTag.setUpdatedAt(Instant.now());
        return categoryTagRepository.save(existingTag);
    }

    private TagSortField validateAndNormalizeTagSortBy(String sortBy) {
       return TagSortField.fromString(sortBy);
    }

    private String determineSortDirection(String sortDirection, TagSortField sortBy) {
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        }

        return switch (sortBy){
            case CREATED_AT, USAGE -> "DESC";
            default -> "ASC"; // Name
        };
    }

    private Mono<Void> validateCategoryExists(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new ApiException("Category not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .then();
    }

    private Mono<Void> validateTagExists(UUID tagId){
        return categoryTagRepository.findById(tagId)
                .switchIfEmpty(Mono.error(new ApiException("Tag not found", ErrorCode.RESOURCE_NOT_FOUND)))
                .then();

    }

    private Mono<Void> checkNotAlreadyAssigned(UUID categoryId, UUID tagId){
        return categoryTagAssignmentRepository.existsByCategoryIdAndTagId(categoryId, tagId)
                .flatMap(exists -> {
                    if(exists){
                        return Mono.error(new ApiException("Tag already assigned to this category", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateAssignmentExists(UUID categoryId, UUID tagId) {
        return categoryTagAssignmentRepository.existsByCategoryIdAndTagId(categoryId, tagId)
                .flatMap(exists -> {
                    if(!exists){
                        return Mono.error(new ApiException("Tag not assigned to this category", ErrorCode.VALIDATION_FAILED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateTagLimit(UUID categoryId){
        return categoryTagAssignmentRepository.countByCategoryId(categoryId)
                .flatMap(currentCount -> {
                    if(currentCount> MAX_TAGS_PER_CATEGORY){
                        return Mono.error(new ApiException(
                                String.format("Cannot add tag. Category already has %d tags (maximum %d)",
                                        MAX_TAGS_PER_CATEGORY, currentCount),
                                ErrorCode.VALIDATION_FAILED
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateTagIds(List <UUID> tagIds){
        if (tagIds == null || tagIds.isEmpty()) {
            return Mono.empty();
        }

        if (tagIds.size() > 3) {
            return Mono.error(new ApiException(
                    String.format("Cannot assign more than %d tags at once", MAX_TAGS_PER_CATEGORY),
                    ErrorCode.VALIDATION_FAILED));
        }
        return Mono.empty();
    }

    private Mono<CategoryTagAssignmentEntity> createAssignment(UUID categoryId, UUID tagId, UUID userId){
        CategoryTagAssignmentEntity assignment = CategoryTagAssignmentEntity.builder()
                .categoryId(categoryId)
                .tagId(tagId)
                .assignedBy(userId)
                .assignedAt(Instant.now())
                .build();
        return categoryTagAssignmentRepository.save(assignment);
    }

    private Mono<CategoryTagAssignmentResponse> mapAssignmentToResponse(CategoryTagAssignmentEntity assignment){
        return Mono.zip(
                categoryRepository.findById(assignment.getCategoryId()),
                categoryTagRepository.findById(assignment.getTagId()),
                appUserService.getUserDetails(assignment.getAssignedBy())
        ).map(tuple -> {
            CategoryEntity category = tuple.getT1();
            CategoryTagEntity tag = tuple.getT2();
            UserDetails userDetails = tuple.getT3();
            return CategoryTagAssignmentResponse.builder()
                    .categoryId(assignment.getCategoryId())
                    .categoryName(category.getName())
                    .tagId(assignment.getTagId())
                    .tagName(tag.getName())
                    .tagDescription(tag.getDescription())
                    .assignedBy(assignment.getAssignedBy())
                    .assignedByDisplayName(userDetails.getDisplayName())
                    .assignedAt(assignment.getAssignedAt())
                    .build();
        });
    }


    /**
     * Enriches a single category tag..
     */
    private Mono<CategoryTagResponse> enrichSingleTagWithData(CategoryTagEntity tag){

        return Mono.zip(
                appUserService.getUserDetails(tag.getCreatedBy()),
                categoryTagRepository.countByTagId(tag.getId())
        ).map(tuple -> {
            UserDetails userDetails = tuple.getT1();
            Long usage = tuple.getT2();

            return CategoryTagResponse.builder()
                    .id(tag.getId())
                    .name(tag.getName())
                    .slug(tag.getSlug())
                    .description(tag.getDescription())
                    .createdBy(tag.getCreatedBy())
                    .createdByDisplayName(userDetails.getDisplayName())
                    .usage(usage.intValue())
                    .createdAt(tag.getCreatedAt())
                    .updatedAt(tag.getUpdatedAt())
                    .build();
        });
    }

    /**
     * Enriches a list of tags with usage counts and creator details using batch fetching.
     * Uses batch fetching to avoid N+1 queries.
     */
    private Mono<List<CategoryTagResponse>> enrichTagsWithBatchData(
        List<CategoryTagEntity> tags
    ){
        if(tags.isEmpty()){
            return Mono.just(List.of());
        }

        List<UUID> tagIds = tags.stream()
                .map(CategoryTagEntity::getId)
                .toList();

        List<UUID> creatorsIds = tags.stream()
                .map(CategoryTagEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Batch fetch usage counts
        Mono<Map<UUID, Long>> usageCountMap = categoryTagAssignmentRepository
                .findUsageCountForTags(tagIds)
                .collectMap(TagUsageRecord::tag_id, TagUsageRecord::count)
                .defaultIfEmpty(new HashMap<>());

        // Batch fetch creator details
        Mono<Map<UUID, UserDetails>> creatorDetailsMap = Mono.just(new HashMap<>());
        if(!creatorsIds.isEmpty()){
            creatorDetailsMap = appUserRepository
                    .findAppUsersByKeycloakIds(creatorsIds)
                    .collectMap(AppUserEntity::getKeycloakId, AppUserEntity::toUserDetails)
                    .defaultIfEmpty(new HashMap<>());

        }

        return Mono.zip(usageCountMap, creatorDetailsMap)
                .map(tuple ->{
                    Map<UUID, Long> usageCounts = tuple.getT1();
                    Map<UUID, UserDetails> creatorDetails = tuple.getT2();

                    return tags.stream()
                            .map(tag -> {
                                Long usage = usageCounts.getOrDefault(tag.getId(), 0L);
                                UserDetails creator = creatorDetails.get(tag.getCreatedBy());

                                return CategoryTagResponse.builder()
                                        .id(tag.getId())
                                        .name(tag.getName())
                                        .slug(tag.getSlug())
                                        .description(tag.getDescription())
                                        .createdBy(tag.getCreatedBy())
                                        .createdByDisplayName(creator.getDisplayName())
                                        .usage(usage.intValue())
                                        .createdAt(tag.getCreatedAt())
                                        .updatedAt(tag.getUpdatedAt())
                                        .build();
                            })
                            .toList();
                });
    }

}
