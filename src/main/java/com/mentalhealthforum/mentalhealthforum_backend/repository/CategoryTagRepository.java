package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.CategoryTagWithCategoryId;
import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryTagEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryTagRepository extends R2dbcRepository<CategoryTagEntity, UUID> {

    Mono<CategoryTagEntity> findByName(String name);

    Mono<CategoryTagEntity> findBySlug(String name);

    Mono<Boolean> existsByName(String name);

    Mono<Boolean> existsBySlug(String slug);

    /**
     * Batch fetch tags for multiple categories.
     * Returns a map of categoryId → List<CategoryTagResponse>
     */
    @Query("""
        SELECT a.category_id, t.*
        FROM category_tags t
        JOIN category_tag_assignments a ON t.id = a.tag_id
        WHERE a.category_id IN (:categoryIds)
        ORDER BY t.name ASC
    """)
    Flux<CategoryTagWithCategoryId> findTagsByCategoryId(@Param("categoryIds") List<UUID> categoryIds);


    @Query("""
        SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
        FROM category_tags
        WHERE slug = :slug
        AND id != :excludedTagId
    """)
    Mono<Boolean> existsBySlugAndIdNot(@Param("slug") String slug, @Param("excludedTagId") UUID excludedTagId);

    @Query("""
        SELECT t.* FROM category_tags t
        JOIN category_tag_assignments a ON t.id = a.tag_id
        WHERE category_id = :categoryId
        ORDER BY t.name ASC
    """)
    Flux<CategoryTagEntity> findByCategoryId(
            @Param("categoryId") UUID categoryId
    );

    @Query("""
        SELECT t.* FROM category_tags t
        WHERE (:search IS NULL

                OR to_tsvector('public.english_unaccent', coalesce(t.name, '') || ' ' || coalesce(t.description, ''))
                    @@ websearch_to_tsquery('public.english_unaccent', :search)
    
                OR public.unaccent_immutable(t.name) % public.unaccent_immutable(:search)
    
                OR public.unaccent_immutable(t.description) % public.unaccent_immutable(:search)
    
        )
    
        ORDER BY
            CASE :sortDirection
                WHEN 'DESC' THEN
                   CASE :sortBy
                        WHEN 'name' THEN t.name
                        WHEN 'created_at' THEN t.created_at::text
                        WHEN 'usage' THEN (SELECT COUNT(*) FROM category_tag_assignments WHERE tag_id = t.id)::text
                        ELSE t.name
                    END
                ELSE NULL
            END DESC NULLS LAST,
    
            CASE :sortDirection
                WHEN 'ASC' THEN
                   CASE :sortBy
                        WHEN 'name' THEN t.name
                        WHEN 'created_at' THEN t.created_at::text
                        WHEN 'usage' THEN (SELECT COUNT(*) FROM category_tag_assignments WHERE tag_id = t.id)::text
                        ELSE t.name
                    END
                ELSE NULL
            END ASC NULLS FIRST
        LIMIT :limit OFFSET :offset
    """)
    Flux<CategoryTagEntity> searchTags(
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
    
    @Query("""
        SELECT COUNT(*) FROM category_tags t
        WHERE (:search IS NULL

                OR to_tsvector('public.english_unaccent', coalesce(t.name, '') || ' ' || coalesce(t.description, ''))
                    @@ websearch_to_tsquery('public.english_unaccent', :search)
    
                OR public.unaccent_immutable(t.name) % public.unaccent_immutable(:search)
    
                OR public.unaccent_immutable(t.description) % public.unaccent_immutable(:search)
    
        )
    """)
    Mono<Long> countSearchTags(
            @Param("search") String search
    );

    @Query("SELECT COUNT(*) FROM category_tag_assignments WHERE tag_id = :tagId")
    Mono<Long> countByTagId(@Param("tagId") UUID tagId);

}