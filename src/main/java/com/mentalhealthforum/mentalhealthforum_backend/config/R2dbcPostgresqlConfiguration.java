package com.mentalhealthforum.mentalhealthforum_backend.config;

import com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters.ProfileVisibilityReadingConverter;
import com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters.ProfileVisibilityWritingConverter;
import com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters.SupportRoleReadingConverter;
import com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters.SupportRoleWritingConverter;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.codec.EnumCodec;
import io.r2dbc.spi.ConnectionFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.Arrays;
import java.util.List;

/**
 * R2DBC PostgreSQL configuration.
 * Registers enum codecs for Postgres ENUM types to map automatically to Java enums.
 */
@Configuration
@EnableR2dbcRepositories
public class R2dbcPostgresqlConfiguration extends AbstractR2dbcConfiguration {

    private final R2dbcProperties properties;

    public R2dbcPostgresqlConfiguration(R2dbcProperties properties) {
        this.properties = properties;
    }

    /**
     * Builds PostgresqlConnectionConfiguration with all enum codecs registered.
     */
    private PostgresqlConnectionConfiguration buildPostgresqlConfiguration() {
        return PostgresqlConnectionConfiguration.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .database(properties.getDatabase())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .codecRegistrar(EnumCodec.builder()
                        // Register all Postgres ENUMs here
                        .withEnum("profile_visibility_enum", ProfileVisibility.class)
                        .withEnum("support_role_enum", SupportRole.class)
                        .build())
                .build();
    }

    /**
     * The R2DBC connection factory bean.
     */
    @NotNull
    @Bean
    @Override
    public ConnectionFactory connectionFactory() {
        return new PostgresqlConnectionFactory(buildPostgresqlConfiguration());
    }

    @NotNull
    @Override
    protected List<Object> getCustomConverters() {
        return Arrays.asList(
                new ProfileVisibilityWritingConverter(),
                new ProfileVisibilityReadingConverter(),
                new SupportRoleWritingConverter(),
                new SupportRoleReadingConverter()
        );
    }

    @NotNull
    @Bean
    @Override
    public R2dbcCustomConversions r2dbcCustomConversions(){
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, getCustomConverters());
    }
}
