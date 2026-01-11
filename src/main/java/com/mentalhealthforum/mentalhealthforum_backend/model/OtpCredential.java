package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OtpPurpose;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table("otp_credentials")
public class OtpCredential {

    @Id
    private final Long id;

    @Column("email")
    private final String email;

    @Column("code_hash")
    private final String codeHash;

    @Column("purpose")
    private final OtpPurpose purpose;

    @Column("expiry_date")
    private final Instant expiryDate;

    @ReadOnlyProperty // Tells Spring Data R2DBC: "Don't try to INSERT this, just READ it"
    @Column("created_at")
    private Instant createdAt;

    public OtpCredential(
            Long id,
            String email,
            String codeHash,
            OtpPurpose purpose,
            Instant expiryDate) {
        this.id = id;
        this.email = email;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.expiryDate = expiryDate;
    }

    public boolean isExpired(){
        return Instant.now().isAfter(this.expiryDate);
    }
}
