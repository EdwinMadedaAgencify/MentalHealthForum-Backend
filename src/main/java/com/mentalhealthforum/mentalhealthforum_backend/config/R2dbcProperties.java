package com.mentalhealthforum.mentalhealthforum_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "spring.r2dbc")
public class R2dbcProperties {
    // Getters and Setters
    private String host = "localhost";
    private int port = 5432;
    private String database;
    private String username;
    private String password;
    private String url;
}
