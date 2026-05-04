package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.SlugGenerationResponse;

import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.CreateForumCategoryRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryHierarchyDto;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ForumCategoryTagRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.UpdateForumCategoryRequest;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryTagEntity;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumCategoryRepository;
import com.mentalhealthforum.mentalhealthforum_backend.repository.ForumCategoryTagRepository;
import com.mentalhealthforum.mentalhealthforum_backend.service.ForumCategoryService;
import com.mentalhealthforum.mentalhealthforum_backend.utils.SlugsUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
    
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
public class ForumCategoryServiceImpl implements ForumCategoryService {

    private final TransactionalOperator transactionalOperator;
    private final ForumCategoryRepository forumCategoryRepository;
    private final ForumCategoryTagRepository forumCategoryTagRepository;

    public ForumCategoryServiceImpl(TransactionalOperator transactionalOperator,
                                    ForumCategoryRepository forumCategoryRepository,
                                    ForumCategoryTagRepository forumCategoryTagRepository) {
        this.transactionalOperator = transactionalOperator;
        this.forumCategoryRepository = forumCategoryRepository;
        this.forumCategoryTagRepository = forumCategoryTagRepository;
    }

    // ==================== SLUG GENERATION ====================

    @Override
    public Mono<SlugGenerationResponse> generateSlug(String name, UUID excludeCategoryId) {
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
    public Mono<ForumCategoryEntity> createCategory(CreateForumCategoryRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = SlugsUtil.generateSlug(request.getName());
        }
        String finalSlug = slug;

        return validateParentCategory(request.getParentCategoryId())
                .then(validateNoDuplicates(request.getName(), finalSlug))
                .then(createAndSaveCategory(request, finalSlug))
                .flatMap(savedCategory -> addTagsToCategory(savedCategory, request.getTags())
                        .thenReturn(savedCategory))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ForumCategoryEntity> updateCategory(UUID id, UpdateForumCategoryRequest request) {
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
    public Mono<Void> deleteCategory(UUID id) {
        return forumCategoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Category with id '"+ id +"' not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                )))
                .flatMap(category -> {
                    category.setIsActive(false);
                    return forumCategoryRepository.save(category).then();
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ForumCategoryEntity> getCategoryById(UUID id) {
        return forumCategoryRepository.findById(id)
                .switchIfEmpty(Mono.error((new ApiException(
                        "Category with id '"+ id +"' not found",
                        ErrorCode.RESOURCE_NOT_FOUND))));
    }

    @Override
    public Mono<ForumCategoryEntity> getCategoryBySlug(String slug) {
        return forumCategoryRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error((new ApiException(
                        "Category with slug '"+ slug +"' not found",
                        ErrorCode.RESOURCE_NOT_FOUND))));
    }

    @Override
    public Flux<ForumCategoryEntity> getAllActiveCategories() {
        return forumCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }

    // ==================== HIERARCHY ====================

    @Override
    public Flux<ForumCategoryHierarchyDto> getCategoryHierarchy() {
        return forumCategoryRepository.findRootCategories()
                .flatMap(root -> Mono.zip(
                        Mono.just(root),
                        forumCategoryRepository.findChildCategories(root.getId()).collectList(),
                        forumCategoryTagRepository.findByCategoryId(root.getId()).collectList()
                ))
                .map(tuple -> new ForumCategoryHierarchyDto(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ));
    }

    @Override
    public Flux<ForumCategoryEntity> getRootCategories() {
        return forumCategoryRepository.findRootCategories();
    }

    @Override
    public Flux<ForumCategoryEntity> getChildCategories(UUID parentId) {
        return forumCategoryRepository.findChildCategories(parentId);
    }

    // ==================== TAG OPERATIONS ====================

    @Override
    public Flux<ForumCategoryTagEntity> getTags(UUID categoryId) {
        return forumCategoryTagRepository.findByCategoryId(categoryId);
    }

    @Override
    public Mono<ForumCategoryTagEntity> addTag(UUID categoryId, ForumCategoryTagRequest request) {
        return forumCategoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error((new ApiException(
                                "Category with id '"+ categoryId +"' not found",
                                ErrorCode.RESOURCE_NOT_FOUND))))
                .flatMap(category ->
                        forumCategoryTagRepository.existsByCategoryIdAndTagName(categoryId, request.name())
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new ApiException(
                                                "Tag '" + request.name() + "' already exists",
                                                ErrorCode.TAG_ALREADY_EXISTS
                                        ));
                                    }
                                    return saveTag(categoryId, request);
                                })
                )
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ForumCategoryTagEntity> updateTagDescription(UUID categoryId, String tagName, String description) {
        return forumCategoryTagRepository.findByCategoryIdAndTagName(categoryId, tagName)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Tag '"+ tagName + "' not found in category '" + categoryId + "'",
                        ErrorCode.TAG_NOT_FOUND)))
                .flatMap(tag -> {
                    tag.setTagDescription(description);
                    return forumCategoryTagRepository.save(tag);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> removeTag(UUID categoryId, String tagName) {
        return forumCategoryTagRepository.findByCategoryIdAndTagName(categoryId, tagName)
                .switchIfEmpty(Mono.error(new ApiException(
                        "Tag '"+ tagName + "' not found in category '" + categoryId + "'",
                        ErrorCode.TAG_NOT_FOUND)))
                .flatMap(tag -> forumCategoryTagRepository.deleteByCategoryIdAndTagName(categoryId, tagName))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> replaceTags(UUID categoryId, List<ForumCategoryTagRequest> tags) {
        return forumCategoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error((new ApiException(
                "Category with id '"+ categoryId +"' not found",
                ErrorCode.RESOURCE_NOT_FOUND))))
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
                .switchIfEmpty(Mono.error((new ApiException(
                        "Parent category with id '"+ parentCategoryId +"' not found",
                        ErrorCode.RESOURCE_NOT_FOUND))))
                .flatMap(parent -> {
                    if (parent.getParentCategoryId() != null) {
                        return Mono.error(new ApiException(
                                "Cannot create subcategory under a child category (only one level allowed)",
                                ErrorCode.INVALID_HIERARCHY
                        ));
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
        ForumCategoryEntity entity = ForumCategoryEntity.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .colorTheme(request.getColorTheme())
                .parentCategoryId(request.getParentCategoryId())
                .contentWarningType(request.getContentWarningType())
                .contentWarningCustomText(request.getContentWarningCustomText())
                .sortOrder(request.getSortOrder())
                .build();

        if (request.getParticipationRequirements() != null) {
            entity.setParticipationRequirements(request.getParticipationRequirements());
        }
        if (request.getDefaultThreadSettings() != null) {
            entity.setDefaultThreadSettings(request.getDefaultThreadSettings());
        }
        return forumCategoryRepository.save(entity);
    }

    private Mono<ForumCategoryTagEntity> saveTag(UUID categoryId, ForumCategoryTagRequest request) {
        ForumCategoryTagEntity tag = ForumCategoryTagEntity.builder()
                .categoryId(categoryId)
                .tagName(request.name())
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
        if (request.getParticipationRequirements() != null) {
            existing.setParticipationRequirements(request.getParticipationRequirements());
        }
        if (request.getDefaultThreadSettings() != null) {
            existing.setDefaultThreadSettings(request.getDefaultThreadSettings());
        }

        if (request.getTags() != null) {
            return forumCategoryTagRepository.deleteByCategoryId(existing.getId())
                    .then(addTagsToCategory(existing, request.getTags()))
                    .then(forumCategoryRepository.save(existing));
        }

        return forumCategoryRepository.save(existing);
    }
}



