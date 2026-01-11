package com.mentalhealthforum.mentalhealthforum_backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "spring.security.encryption")
public class EncryptionProperties {

    @NotBlank(message = "Encryption key must not be blank")
    @Size(min = 16, max = 32, message = "AES key must be 16, 24, or 32 characters long")
    private String key;
}
