package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.model.WatchThreadEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface WatchThreadRepository extends R2dbcRepository<WatchThreadEntity, UUID> {

//    Mono<Boolean> existsByUserIdAndThreadId(UUID userId, UUID connectedUserId);
//
//    Mono<Void> deleteByUserIdAndThreadId(UUID userId, UUID connectedUserId);
//
//    Flux<WatchThreadEntity> findByUserId(UUID userId);
//
//    Flux<WatchThreadEntity> findByThreadId(UUID connectedUserId);
//
//    Mono<Long> countByThreadId(UUID connectedUserId);
//
//    Mono<Long> countByUserId(UUID userId);

}
