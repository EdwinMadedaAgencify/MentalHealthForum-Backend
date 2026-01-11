package com.mentalhealthforum.mentalhealthforum_backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("pending_users")
public record PendingUser(
        @Id
        Long id,

        @Column("username")
        String username,

        @Column("email")
        String email,

        @Column("encrypted_password")
        String encryptedPassword,

        @Column("first_name")
        String firstName,

         @Column("last_name")
        String lastName
){}
