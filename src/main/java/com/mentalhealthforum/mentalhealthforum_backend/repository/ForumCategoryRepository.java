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
    // ==================== BASIC QUERIES ====================

    Mono<ForumCategoryEntity> findBySlug(String slug);

    Mono<Boolean> existsByName(String name);

    Mono<Boolean> existsBySlug(String slug);

    @Query("""
        SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
        FROM forum_categories
        WHERE slug = :slug
        AND id != :excludedCategoryId
    """)
    Mono<Boolean> existsBySlugAndIdNot(@Param("slug") String slug, @Param("excludedCategoryId") UUID excludedCategoryId);


    // ==================== ACTIVE / INACTIVE ====================

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

    // ==================== HIERARCHY ====================

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
    Flux<ForumCategoryEntity> findChildCategories(@Param("parentId") UUID parentId);

    // ==================== TAG-BASED QUERIES ====================
    @Query("""
        SELECT c.* FROM forum_categories c
        INNER JOIN category_tags ct ON c.id = ct.category_id
        WHERE ct.tagName = :tagName
        AND c.is_active = true
        ORDER BY c.sort_order ASC
    """)

    Flux<ForumCategoryEntity> findCategoriesByTag(@Param("tagName") String tagName);

    // ==================== PAGINATED QUERIES ====================

    @Query("""
        WITH distinct_categories AS (
            SELECT DISTINCT c.* FROM forum_categories c
            LEFT JOIN category_tags ct ON c.id = ct.category_id
            WHERE (:tagName IS NULL OR ct.tag_name = :tagName)
            AND (:parentCategoryId IS NULL OR c.parent_category_id = :parentCategoryId)
            AND (:isParent IS NULL OR
                    (:isParent = true AND c.parent_category_id IS NULL) OR
                    (:isParent = false AND c.parent_category_id IS NOT NULL))
            AND (:search IS NULL OR
                    LOWER(c.name) LIKE '%' || LOWER(:search) || '%' OR
                    LOWER(c.slug) LIKE '%' || LOWER(:search) || '%' OR
                    LOWER(c.description) LIKE '%' || LOWER(:search) || '%')
            AND (:isActive IS NULL OR c.is_active = :isActive)
        )
    
        SELECT * FROM distinct_categories dc
        ORDER BY
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'sort_order' THEN LPAD(dc.sort_order::text, 10, '0')
                        WHEN 'name' THEN dc.name
                        WHEN 'created_at' THEN dc.created_at::text
                        ELSE LPAD(dc.sort_order::text, 10, '0')
                    END
                ELSE NULL
            END DESC NULLS LAST,
    
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'sort_order' THEN LPAD(dc.sort_order::text, 10, '0')
                        WHEN 'name' THEN dc.name
                        WHEN 'created_at' THEN dc.created_at::text
                        ELSE LPAD(dc.sort_order::text, 10, '0')
                    END
                ELSE NULL
            END ASC NULLS FIRST
        LIMIT :limit OFFSET :offset
    """)
    Flux<ForumCategoryEntity> findAllCategoriesPaginated(
            @Param("tagName") String tagName,
            @Param("parentCategoryId") UUID parentCategoryId,
            @Param("isParent") Boolean isParent,
            @Param("search") String search,
            @Param("isActive") Boolean isActive,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(DISTINCT c.id) FROM forum_categories c
        LEFT JOIN category_tags ct ON c.id = ct.category_id
        WHERE (:tagName IS NULL OR ct.tag_name = :tagName)
        AND (:parentCategoryId IS NULL OR c.parent_category_id = :parentCategoryId)
        AND (:isParent IS NULL OR
                (:isParent = true AND c.parent_category_id IS NULL) OR
                (:isParent = false AND c.parent_category_id IS NOT NULL))
        AND (:search IS NULL OR
                LOWER(c.name) LIKE '%' || LOWER(:search) || '%' OR
                LOWER(c.slug) LIKE '%' || LOWER(:search) || '%' OR
                LOWER(c.description) LIKE '%' || LOWER(:search) || '%')
        AND (:isActive IS NULL OR c.is_active = :isActive)
    """)
    Mono<Long> countAllCategoriesWithFilters(
            @Param("tagName") String tagName,
            @Param("parentCategoryId") UUID parentCategoryId,
            @Param("isParent") Boolean isParent,
            @Param("search") String search,
            @Param("isActive") Boolean isActive
    );

}
