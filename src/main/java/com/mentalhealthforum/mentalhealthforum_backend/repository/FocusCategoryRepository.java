package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.FocusCategoryEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface FocusCategoryRepository extends R2dbcRepository<FocusCategoryEntity, UUID> {

//    Mono<Boolean> existsByUseridAndCategoryId(UUID userId, UUID connectedUserId);
//
//    Mono<Void> deleteByUseridAndCategoryId(UUID userId, UUID connectedUserId);
//
//    Flux<FocusCategoryEntity> findByUserId(UUID userId);
//
//    Flux<FocusCategoryEntity>findByCategoryId(UUID connectedUserId);
//
//    Mono<Long> countByCategoryId(UUID connectedUserId);
//
//    Mono<Long> countByUserId(UUID userId);

}
