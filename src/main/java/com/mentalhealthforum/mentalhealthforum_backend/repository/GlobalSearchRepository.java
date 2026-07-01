package com.mentalhealthforum.mentalhealthforum_backend.repository;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.GlobalSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class GlobalSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchRepository.class);

    private final DatabaseClient databaseClient;

    public GlobalSearchRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Slice<GlobalSearchResult>> executeLiveSearch(
            String rawQuery,
            String sortBy,
            Pageable pageable,
            ViewerContext viewerContext){

        // High performance Java Pre-evaluation
        UUID viewerId = null;
        boolean isAdmin = false;
        boolean isModeratorOrAdmin = false;
        boolean isVerified = false;

        if(viewerContext != null && viewerContext.getUserId() != null){
           try{
               viewerId = UUID.fromString(viewerContext.getUserId());
               isAdmin = viewerContext.isAdmin();
               isModeratorOrAdmin = viewerContext.isModeratorOrAdmin();
               isVerified = viewerContext.isTrustedMember() || viewerContext.isPeerSupporter() || viewerContext.isAdmin();
           } catch (IllegalArgumentException e){
               log.error("Failed to parse viewer keycloak UUID string from context: {}", viewerContext.getUserId());
           }
        }
        
        // Resolve sorting boundaries. Safe, optimized outer execution sorting valuables
        String orderByClause= "search_score DESC, header ASC"; // relevance
        if("recent".equalsIgnoreCase(sortBy)){
            orderByClause = "last_activity_at DESC NULLS LAST, header ASC";
        }

        // Entirely consolidated security core search query over active relational states
        String coreSearchUnionSql = """
                -- PERFORMANCE OPTIMIZATION: websearch_to_tsquery called ONCE via CTE
 
                WITH query_token AS (
                    SELECT websearch_to_tsquery('public.english_unaccent', :query) AS tsquery,
                           websearch_to_tsquery('public.simple_unaccent', :query) AS tsquery_simple
                )
                
                -- 1. THREADS
                SELECT
                    t.id AS entity_id,
                    'THREAD'::TEXT AS entity_type,
                    t.title AS header,
                    ts_headline('public.english_unaccent', coalesce(t.title, ''), (SELECT tsquery FROM query_token), 'MaxWords=25, StartSel=<b>, StopSel=</b>')::TEXT AS body_preview,
                    ts_rank(
                        setweight(to_tsvector('public.english_unaccent', coalesce(t.title, '')), 'A'),
                        (SELECT tsquery FROM query_token)
                    ) AS search_score,
                    t.last_activity_at AS last_activity_at
                FROM forum_threads t
                INNER JOIN forum_categories c ON t.category_id = c.id
                WHERE t.is_deleted = FALSE
                    AND to_tsvector('public.english_unaccent', coalesce(t.title, '')) @@ (SELECT tsquery FROM query_token)
                
                    -- Category visibility filter (same as CATEGORIES block below
                    AND c.is_active = TRUE
                    AND (
                         -- PUBLIC: always visible'
                         (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'PUBLIC')
                
                         -- MEMBERS_ONLY: viewer must be logged in
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MEMBERS_ONLY' AND :viewerId IS NOT NULL)
               
                         -- VERIFIED_ONLY: viewer must be trusted, peer supporter or admin
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'VERIFIED_ONLY' AND :isVerified = TRUE)
                
                         -- MODERATORS_ONLY: viewer must be moderator or admin
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MODERATORS_ONLY' AND :isModeratorOrAdmin = TRUE)
                
                         -- ADMINS_ONLY: viewer must be moderator or admin
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'ADMINS_ONLY' AND :isAdmin = TRUE)
                
                        )
              
                UNION ALL
                
                -- 2. POSTS (With Advanced contextual Snippet Surrounding Search Term)
                SELECT
                    p.id AS entity_id,
                    'POST'::TEXT AS entity_type,
                    CASE WHEN p.is_anonymous = TRUE
                        THEN 'Anonymous Reply'::VARCHAR
                        ELSE coalesce(u.display_name, 'forum_member'::VARCHAR)
                    END AS header,
                    ts_headline('public.english_unaccent', coalesce(p.content, ''), (SELECT tsquery FROM query_token), 'MaxWords=25, MinWords=15, StartSel=<b>, StopSel=</b>')::TEXT AS body_preview,
                    ts_rank(
                        setweight(to_tsvector('public.english_unaccent', coalesce(p.content, '')), 'B'),
                        (SELECT tsquery FROM query_token)
                    ) AS search_score,
                    p.created_at AS last_activity_at
                FROM forum_posts p
                LEFT JOIN app_users u ON p.author_id = u.keycloak_id
                INNER JOIN forum_threads t ON p.thread_id = t.id
                INNER JOIN forum_categories c ON t.category_id = c.id
                WHERE p.is_deleted = FALSE
                    AND p.flagged_for_review = FALSE
                    AND to_tsvector('public.english_unaccent', coalesce(p.content, '')) @@ (SELECT tsquery FROM query_token)
              
                    -- Thread must not be deleted
                    AND t.is_deleted = FALSE
               
                    -- Category visibility (same as above)
                    AND c.is_active = TRUE
                    AND (
                
                         (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'PUBLIC')
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MEMBERS_ONLY' AND :viewerId IS NOT NULL)
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'VERIFIED_ONLY' AND :isVerified = TRUE)
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MODERATORS_ONLY' AND :isModeratorOrAdmin = TRUE)
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'ADMINS_ONLY' AND :isAdmin = TRUE)
                
                        )
                
                UNION ALL
                
                -- 3. CATEGORIES & CATEGORY TAGS (with privacy applied directly)
                SELECT
                    c.id AS entity_id,
                    'CATEGORY'::TEXT AS entity_type,
                    c.name AS header,
                    ts_headline('public.english_unaccent',
                        CASE
                            WHEN cat_tags.aggregated_tags IS NOT NULL
                            THEN '[Tags: ' || coalesce(cat_tags.aggregated_tags, '') || '] ' || coalesce(c.description, '')
                            ELSE coalesce(c.description, '')
                        END,
                        (SELECT tsquery FROM query_token),
                        'MaxWords=25, StartSel=<b>, StopSel=</b>'
                    )::TEXT AS body_preview,
                    ts_rank(
                        setweight(to_tsvector('public.english_unaccent', coalesce(c.name, '')), 'A') ||
                        setweight(to_tsvector('public.english_unaccent', coalesce(c.description, '')), 'B') ||
                        setweight(to_tsvector('public.english_unaccent', coalesce(cat_tags.aggregated_tags, '')), 'A'),
                        (SELECT tsquery FROM query_token)
                    ) AS search_score,
                    c.created_at AS last_activity_at
                FROM forum_categories c
                LEFT JOIN (
                    SELECT cta.category_id, string_agg(ct.name, ' ') AS aggregated_tags
                    FROM  category_tag_assignments cta
                    JOIN category_tags ct ON cta.tag_id = ct.id
                    GROUP BY cta.category_id
                ) cat_tags ON c.id = cat_tags.category_id
                WHERE c.is_active = TRUE
                    AND (
                        to_tsvector('public.english_unaccent', coalesce(c.name, '')) @@ (SELECT tsquery FROM query_token) OR
                        to_tsvector('public.english_unaccent', coalesce(c.description, '')) @@ (SELECT tsquery FROM query_token) OR
                        to_tsvector('public.english_unaccent', coalesce(cat_tags.aggregated_tags, '')) @@ (SELECT tsquery FROM query_token)
                    )
               
                    -- Category visibility (same as above)
                    AND (
                
                         (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'PUBLIC')
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MEMBERS_ONLY' AND :viewerId IS NOT NULL)
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'VERIFIED_ONLY' AND :isVerified = TRUE)
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'MODERATORS_ONLY' AND :isModeratorOrAdmin = TRUE)
                         OR (COALESCE(c.participation_requirements ->> 'viewAccess', 'MEMBERS_ONLY') = 'ADMINS_ONLY' AND :isAdmin = TRUE)
                
                        )
               
                UNION ALL
                -- 4. USER PROFILES (Display Name + Bio)
                SELECT
                    u.id AS entity_id,
                    'PROFILE'::TEXT AS entity_type,
                    u.display_name AS header,
                    ts_headline('public.simple_unaccent', coalesce(u.bio, ''), (SELECT tsquery_simple FROM query_token), 'MaxWords=25, StartSel=<b>, StopSel=</b>')::TEXT AS body_preview,
                    ts_rank(
                        setweight(to_tsvector('public.simple_unaccent', coalesce(u.display_name, '')), 'A') ||
                        setweight(to_tsvector('public.simple_unaccent', coalesce(u.bio, '')), 'B'),
                        (SELECT tsquery_simple FROM query_token)
                    ) AS search_score,
                    u.last_active_at AS last_activity_at
                FROM app_users u
                WHERE u.is_active = TRUE
                    AND u.account_deletion_requested_at IS NULL
                    AND (
                          to_tsvector('public.simple_unaccent', coalesce(u.display_name, '')) @@ (SELECT tsquery_simple FROM query_token) OR
                          to_tsvector('public.simple_unaccent', coalesce(u.bio, '')) @@ (SELECT tsquery_simple FROM query_token)
                    )
                
                    -- Privacy filter
                    AND (
                        -- MEMBERS_ONLY: viewer must be logged in
                        (u.profile_visibility = 'MEMBERS_ONLY' AND :viewerId IS NOT NULL)
                
                        -- PRIVATE: owner, admin, moderator, or mutual connection
                        OR(u.profile_visibility = 'PRIVATE' AND (
                            -- Owner
                            u.keycloak_id = :viewerId
                
                            -- Admin or moderator
                            OR :isAdmin = TRUE
                            OR :isModeratorOrAdmin = TRUE
               
                            -- Mutual connection (ACCEPTED)
                            OR EXISTS (
                                SELECT 1 FROM user_connections uc
                                WHERE (
                                    (uc.user_1 = u.keycloak_id AND uc.user_2 = :viewerId) OR
                                    (uc.user_1 = :viewerId AND uc.user_2 = u.keycloak_id)
                                )
                                AND uc.status = 'ACCEPTED'::connection_status_enum
                            )
              
                        ))
                
                    )
                """;

        // Pagination: Fetch one extra row to check if there's a next page
        int fetchSize = pageable.getPageSize() + 1;

        // The outer query is now crystal clear
        String dataSql = "WITH combined_search AS (" + coreSearchUnionSql + ")" +
                "SELECT entity_id, entity_type, header, body_preview, search_score, last_activity_at " +
                "FROM combined_search " +
                "ORDER BY " + orderByClause + " LIMIT :limit OFFSET :offset";

        // Log the search parameters
        log.debug("Search query: '{}', sortBy: '{}', page: {}, size: {}, offset: {}",
                rawQuery, sortBy, pageable.getPageNumber(), pageable.getPageSize(), pageable.getOffset());

        // Log the full SQL being executed
        log.debug("SQL: {}", dataSql);

        // Secure R2DBC Null-safe Parameters Binding Execution
        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(dataSql)
                .bind("query", rawQuery)
                .bind("isAdmin", isAdmin)
                .bind("isModeratorOrAdmin", isModeratorOrAdmin)
                .bind("isVerified", isVerified)
                .bind("limit", fetchSize)
                .bind("offset", (int) pageable.getOffset());


        // Defend against R2DBC primitive null column crashes via driver fallback assignment
        if(viewerId != null){
            executeSpec = executeSpec.bind("viewerId", viewerId);
        }
        else{
            executeSpec = executeSpec.bindNull("viewerId", UUID.class);
        }


        return executeSpec
                .map((row, metadata) -> {
                    LocalDateTime dateTime = row.get("last_activity_at", LocalDateTime.class);
                    Instant instant = (dateTime != null)? dateTime.toInstant(ZoneOffset.UTC) : null;

                    return new GlobalSearchResult(
                            row.get("entity_id", UUID.class),
                            row.get("entity_type", String.class),
                            row.get("header", String.class),
                            row.get("body_preview", String.class),
                            row.get("search_score", Double.class),
                            instant
                    );
                })
                .all()
                .collectList()
                .map(results -> {
                    boolean hasNext = results.size() > pageable.getPageSize();
                    List<GlobalSearchResult> content = hasNext
                            ? results.subList(0, pageable.getPageSize())
                            : results;

                    return new SliceImpl<>(content, pageable, hasNext);
                });
    }
}
