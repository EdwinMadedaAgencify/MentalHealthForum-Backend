package com.mentalhealthforum.mentalhealthforum_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import com.mentalhealthforum.mentalhealthforum_backend.enums.OtpPurpose;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.codec.EnumCodec;
import io.r2dbc.spi.ConnectionFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;


import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;

    public R2dbcPostgresqlConfiguration(R2dbcProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
                        .withEnum("onboarding_stage_enum", OnboardingStage.class)
                        .withEnum("otp_purpose_enum", OtpPurpose.class)
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
                new SupportRoleReadingConverter(),
                new OnboardingStageWritingConverter(),
                new OnboardingStageReadingConverter(),
                new OtpPurposeWritingConverter(),
                new OtpPurposeReadingConverter()
        );
    }

    @NotNull
    @Bean
    @Override
    public R2dbcCustomConversions r2dbcCustomConversions(){
        List<Converter<?, ?>> converters = new ArrayList<>();
        // Directly add all the custom converters
        converters.add(new ProfileVisibilityWritingConverter());
        converters.add(new ProfileVisibilityReadingConverter());
        converters.add(new SupportRoleWritingConverter());
        converters.add(new SupportRoleReadingConverter());
        converters.add(new OnboardingStageWritingConverter());
        converters.add(new OnboardingStageReadingConverter());
        converters.add(new OtpPurposeWritingConverter());
        converters.add(new OtpPurposeReadingConverter());

        // Add JSONB converters
        converters.add(new JsonNodeToJsonWriteConverter(objectMapper));
        converters.add(new JsonToJsonNodeReadConverter(objectMapper));
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
    }
}
