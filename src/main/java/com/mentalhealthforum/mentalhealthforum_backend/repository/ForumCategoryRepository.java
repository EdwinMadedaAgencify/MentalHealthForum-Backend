package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.ForumCategoryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ForumCategoryRepository extends R2dbcRepository<ForumCategoryEntity, UUID> {
    Mono<ForumCategoryEntity> findBySlug(String slug);

    Flux<ForumCategoryEntity> findByIsActiveTrueOrderBySortOrderAsc();

    Flux<ForumCategoryEntity> findByIsActiveFalse();

    Mono<Long> countByIsActiveFalse();

    @Query("""
        SELECT c.* FROM forum_categories c
        WHERE c.is_active = false
        AND c.created_at < :cutoffDate
        ORDER BY c.created_at ASC
    """)
    Flux<ForumCategoryEntity> findInactiveCategoriesOlderThan(@Param("cutoffDate") Instant cutoffDate);


    @Query("""
        SELECT c.* FROM forum_categories c
        WHERE c.is_active = true
        AND c.parent_category_id IS NULL
        ORDER BY c.sort_order ASC
    """)
    Flux<ForumCategoryEntity> findRootCategories();

    @Query("""
        SELECT c.* FROM forum_categories c
        WHERE c.parent_category_id = :parentId
        AND c.is_active = true
        ORDER BY c.sort_order ASC
    """)
    Flux<ForumCategoryEntity> findChildCategories(UUID parentId);

    @Query("""
        SELECT c.* FROM forum_categories c
        INNER JOIN category_tags ct ON c.id = ct.category_id
        WHERE ct.tagName = :tagName
        AND c.is_active = true
        ORDER BY c.sort_order ASC
    """)
    Flux<ForumCategoryEntity> findCategoriesByTag(String tagName);

    Mono<Boolean> existsByName(String name);

    Mono<Boolean> existsBySlug(String slug);

    @Query("""
        SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
        FROM forum_categories
        WHERE slug = :slug
        AND id != :excludedCategoryId
    """)
    Mono<Boolean> existsBySlugAndIdNot(String slug, UUID excludedCategoryId);
}
