package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table("verification_tokens")
public class VerificationToken {
    @Id
    private final Long id;

    @Column("token")
    private final String token;

    @Column("email") // This remains the "current" primary identifier (source)
    private final String email;

    @Column("new_value") // This stores the "proposed" change (destination)
    private final  String newValue;

    @Column("expiry_date")
    private final Instant expiryDate;

    @Column("type")
    private final VerificationType type; // Store as String for R2DBC simplicity: "INVITED" or "SELF_REG"

    @Column("group_path")
    private final String groupPath;

    @ReadOnlyProperty
    @Column("created_at")
    private Instant createdAt;

    public VerificationToken(
            Long id,
            String token,
            String email,
            Instant expiryDate,
            VerificationType type,
            String groupPath,
            String newValue) {
        this.id = id;
        this.token = token;
        this.email = email;
        this.newValue = newValue;
        this.expiryDate = expiryDate;
        this.type = type;
        this.groupPath = groupPath;
    }

    public boolean isExpired(){
        return Instant.now().isAfter(this.expiryDate);
    }
}
