package com.mentalhealthforum.mentalhealthforum_backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The core application entity representing a forum user.
 * In this minimal configuration, the entity only stores the Keycloak ID,
 * acting primarily as a foreign key and a marker for user existence in the
 * local database. All identity details (name, email) must be sourced from Keycloak.
 */
@Table("app_user")
public record User(
        // The Keycloak ID serves as the Primary Key in our application database.
        // This is the ONLY field needed for initial user setup/linking.
        @Id
        @Column("id")
        String id
) {
    // Future application-specific fields (e.g., postCount, reputationScore)
    // can be added here once the requirements are defined.
}

