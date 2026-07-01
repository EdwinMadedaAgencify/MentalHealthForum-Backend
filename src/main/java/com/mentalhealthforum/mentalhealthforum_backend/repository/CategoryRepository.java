package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends R2dbcRepository<CategoryEntity, UUID> {
    // ==================== BASIC QUERIES ====================

    Mono<CategoryEntity> findBySlug(String slug);

    Mono<Boolean> existsByName(String name);

    Mono<Boolean> existsBySlug(String slug);

    /**
     * Batch fetch categories by IDs
     */
    @Query("SELECT * FROM forum_categories WHERE id IN (:ids)")
    Flux<CategoryEntity> findCategoriesByIds(@Param("ids") List<UUID> ids);

    @Query("""
        SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
        FROM forum_categories
        WHERE slug = :slug
        AND id != :excludedCategoryId
    """)
    Mono<Boolean> existsBySlugAndIdNot(@Param("slug") String slug, @Param("excludedCategoryId") UUID excludedCategoryId);


    // ==================== ACTIVE / INACTIVE ====================

    Flux<CategoryEntity> findByIsActiveTrueOrderBySortOrderAsc();

    Flux<CategoryEntity> findByIsActiveFalse();

    Mono<Long> countByIsActiveFalse();

    @Query("""
        SELECT c.* FROM forum_categories c
        WHERE c.is_active = false
        AND c.created_at < :cutoffDate
        ORDER BY c.created_at ASC
    """)
    Flux<CategoryEntity> findInactiveCategoriesOlderThan(@Param("cutoffDate") Instant cutoffDate);

    // ==================== HIERARCHY ====================

    @Query("""
        SELECT c.* FROM forum_categories c
        WHERE c.is_active = true
        AND c.parent_category_id IS NULL
        ORDER BY c.sort_order ASC
    """)
    Flux<CategoryEntity> findRootCategories();

    @Query("""
        SELECT c.* FROM forum_categories c
        WHERE c.parent_category_id = :parentId
        AND c.is_active = true
        ORDER BY c.sort_order ASC
    """)
    Flux<CategoryEntity> findChildCategories(@Param("parentId") UUID parentId);

    // ==================== TAG-BASED QUERIES ====================
    @Query("""
        SELECT c.* FROM forum_categories c
        INNER JOIN category_tags ct ON c.id = ct.category_id
        WHERE ct.tagName = :tagName
        AND c.is_active = true
        ORDER BY c.sort_order ASC
    """)

    Flux<CategoryEntity> findCategoriesByTag(@Param("tagName") String tagName);

    // ==================== PAGINATED QUERIES ====================

    @Query("""
        SELECT c.* FROM forum_categories c
            WHERE (:parentCategoryId IS NULL OR c.parent_category_id = :parentCategoryId)
            AND (:isParent IS NULL OR
                    (:isParent = true AND c.parent_category_id IS NULL) OR
                    (:isParent = false AND c.parent_category_id IS NOT NULL))

            -- Search: FTS + Trigram (User existing GIN index)
            AND (:search IS NULL
    
                OR to_tsvector('public.english_unaccent', coalesce(c.name, '') || ' ' || coalesce(c.description, ''))
                    @@ websearch_to_tsquery('public.english_unaccent', :search)

                OR public.unaccent_immutable(c.name) % public.unaccent_immutable(:search)
                OR public.unaccent_immutable(c.description) % public.unaccent_immutable(:search)
            )
    
            AND (:isActive IS NULL OR c.is_active = :isActive)
    
            -- Tag filter using centralised tags
            AND (:tagId IS NULL OR EXISTS (
                SELECT 1 FROM category_tag_assignments a
                WHERE a.category_id = c.id AND a.tag_id = :tagId
            ))
    
            -- Focused filter
            AND (:isFocused IS NULL OR
                (:isFocused = true AND EXISTS (
                    SELECT 1 FROM focus_categories fc
                    WHERE fc.category_id = c.id AND fc.user_id = :currentUserId
                )) OR
                (:isFocused = false AND NOT EXISTS (
                    SELECT 1 FROM focus_categories fc
                    WHERE fc.category_id = c.id AND fc.user_id = :currentUserId
                ))
        )
    
        ORDER BY
            CASE :sortDirection
                WHEN 'DESC' THEN
                    CASE :sortBy
                        WHEN 'sort_order' THEN LPAD(c.sort_order::text, 10, '0')
                        WHEN 'name' THEN c.name
                        WHEN 'created_at' THEN c.created_at::text
                        ELSE LPAD(c.sort_order::text, 10, '0')
                    END
                ELSE NULL
            END DESC NULLS LAST,
    
            CASE :sortDirection
                WHEN 'ASC' THEN
                    CASE :sortBy
                        WHEN 'sort_order' THEN LPAD(c.sort_order::text, 10, '0')
                        WHEN 'name' THEN c.name
                        WHEN 'created_at' THEN c.created_at::text
                        ELSE LPAD(c.sort_order::text, 10, '0')
                    END
                ELSE NULL
            END ASC NULLS FIRST
        LIMIT :limit OFFSET :offset
    """)
    Flux<CategoryEntity> findAllCategoriesPaginated(
            @Param("currentUserId") UUID currentUserId,
            @Param("tagId") UUID tagId,
            @Param("parentCategoryId") UUID parentCategoryId,
            @Param("isParent") Boolean isParent,
            @Param("isActive") Boolean isActive,
            @Param("isFocused") Boolean isFocused,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
        SELECT COUNT(*) FROM forum_categories c
        WHERE (:parentCategoryId IS NULL OR c.parent_category_id = :parentCategoryId)
        AND (:isParent IS NULL OR
                (:isParent = true AND c.parent_category_id IS NULL) OR
                (:isParent = false AND c.parent_category_id IS NOT NULL))

        AND (:search IS NULL

            OR to_tsvector('public.english_unaccent', coalesce(c.name, '') || ' ' || coalesce(c.description, ''))
                @@ websearch_to_tsquery('public.english_unaccent', :search)

            OR public.unaccent_immutable(c.name) % public.unaccent_immutable(:search)
            OR public.unaccent_immutable(c.description) % public.unaccent_immutable(:search)
        )

        AND (:isActive IS NULL OR c.is_active = :isActive)
    
        -- Tag filter using centralised tags
        AND (:tagId IS NULL OR EXISTS (
            SELECT 1 FROM category_tag_assignments a
            WHERE a.category_id = c.id AND a.tag_id = :tagId
        ))
    
        -- Focused filter
        AND (:isFocused IS NULL OR
            (:isFocused = true AND EXISTS (
                SELECT 1 FROM focus_categories fc
                WHERE fc.category_id = c.id AND fc.user_id = :currentUserId
            )) OR
            (:isFocused = false AND NOT EXISTS (
                SELECT 1 FROM focus_categories fc
                WHERE fc.category_id = c.id AND fc.user_id = :currentUserId
            ))
            )
    """)
    Mono<Long> countAllCategoriesWithFilters(
            @Param("currentUserId") UUID currentUserId,
            @Param("tagId") UUID tagId,
            @Param("parentCategoryId") UUID parentCategoryId,
            @Param("isParent") Boolean isParent,
            @Param("isActive") Boolean isActive,
            @Param("isFocused") Boolean isFocused,
            @Param("search") String search

    );

}
