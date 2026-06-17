package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.CategoryTagAssignmentEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface CategoryTagAssignmentRepository extends R2dbcRepository<CategoryTagAssignmentEntity, UUID> {

    Mono<Boolean> existsByCategoryIdAndTagId(UUID categoryId, UUID tagId);

    Mono<Void> deleteByCategoryId(UUID categoryId);

    Mono<Void> deleteByCategoryIdAndTagId(UUID categoryId, UUID tagId);

    @Query("""
        SELECT COUNT(*) FROM category_tag_assignments
        WHERE category_id = :categoryId
    """)
    Mono<Long> countByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("""
        SELECT * FROM category_tag_assignments
        WHERE category_id = :categoryId
    """)
    Flux<CategoryTagAssignmentEntity> findByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("""
        SELECT * FROM category_tag_assignments
        WHERE category_id = :categoryId
        AND tag_id = :tagId
    """)
    Flux<CategoryTagAssignmentEntity> findByCategoryIdAndTagId(
            @Param("categoryId") UUID categoryId,
            @Param("tagId") UUID tagId
    );

    @Query("""
        SELECT * FROM category_tag_assignments
        WHERE tag_id = :tagId
    """)
    Flux<CategoryTagAssignmentEntity> findByTagId(@Param("tagId") UUID categoryId);


}
