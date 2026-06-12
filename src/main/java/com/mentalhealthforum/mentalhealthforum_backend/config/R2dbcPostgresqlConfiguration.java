package com.mentalhealthforum.mentalhealthforum_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters.*;
import com.mentalhealthforum.mentalhealthforum_backend.enums.*;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.codec.EnumCodec;
import io.r2dbc.spi.ConnectionFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
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
@EnableR2dbcAuditing
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
                        .withEnum("content_warning_enum", ContentWarningType.class)
                        .withEnum("thread_type_enum", ThreadType.class)
                        .withEnum("thread_status_enum", ThreadStatus.class)
                        .withEnum("post_type_enum", PostType.class)
                        .withEnum("edit_reason_enum", EditReason.class)
                        .withEnum("reaction_enum", ReactionType.class)
                        .withEnum("report_target_type_enum", ReportTargetType.class)
                        .withEnum("report_category_enum", ReportCategory.class)
                        .withEnum("severity_enum", Severity.class)
                        .withEnum("report_status_enum", ReportStatus.class)
                        .withEnum("moderation_action_enum", ModerationAction.class)
                        .withEnum("dismissal_reason_enum", DismissalReason.class)
                        .withEnum("report_reason_code_enum", ReportReasonCode.class)
                        .withEnum("warning_type_enum", WarningType.class)
                        .withEnum("restriction_type_enum", RestrictionType.class)
                        .withEnum("connection_status_enum", ConnectionStatus.class)
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
                new OtpPurposeReadingConverter(),
                new ContentWarningTypeWritingConverter(),
                new ContentWarningTypeReadingConverter(),
                new ThreadTypeWritingConverter(),
                new ThreadTypeReadingConverter(),
                new ThreadStatusWritingConverter(),
                new ThreadStatusReadingConverter(),
                new PostTypeReadingConverter(),
                new PostTypeWritingConverter(),
                new EditReasonReadingConverter(),
                new EditReasonWritingConverter(),
                new ReactionTypeReadingConverter(),
                new ReactionTypeWritingConverter(),
                new ReportTargetTypeReadingConverter(),
                new ReportTargetTypeWritingConverter(),
                new ReportCategoryReadingConverter(),
                new ReportCategoryWritingConverter(),
                new SeverityReadingConverter(),
                new SeverityWritingConverter(),
                new ReportStatusReadingConverter(),
                new ReportStatusWritingConverter(),
                new ModerationActionReadingConverter(),
                new ModerationActionWritingConverter(),
                new DismissalReasonReadingConverter(),
                new DismissalReasonWritingConverter(),
                new ReportReasonCodeReadingConverter(),
                new ReportReasonCodeWritingConverter(),
                new WarningTypeReadingConverter(),
                new WarningTypeWritingConverter(),
                new RestrictionTypeReadingConverter(),
                new RestrictionTypeWritingConverter(),
                new ConnectionStatusReadingConverter(),
                new ConnectionStatusWritingConverter()
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
        converters.add(new ContentWarningTypeWritingConverter());
        converters.add(new ContentWarningTypeReadingConverter());
        converters.add(new ThreadTypeWritingConverter());
        converters.add(new ThreadTypeReadingConverter());
        converters.add(new ThreadStatusWritingConverter());
        converters.add(new ThreadStatusReadingConverter());
        converters.add(new PostTypeReadingConverter());
        converters.add(new PostTypeWritingConverter());
        converters.add(new EditReasonWritingConverter());
        converters.add(new EditReasonReadingConverter());
        converters.add(new ReactionTypeReadingConverter());
        converters.add(new ReactionTypeWritingConverter());
        converters.add(new ReportTargetTypeReadingConverter());
        converters.add(new ReportTargetTypeWritingConverter());
        converters.add(new ReportCategoryReadingConverter());
        converters.add(new ReportCategoryWritingConverter());
        converters.add(new SeverityReadingConverter());
        converters.add(new SeverityWritingConverter());
        converters.add(new ReportStatusReadingConverter());
        converters.add(new ReportStatusWritingConverter());
        converters.add(new ModerationActionReadingConverter());
        converters.add(new ModerationActionWritingConverter());
        converters.add(new DismissalReasonReadingConverter());
        converters.add(new DismissalReasonWritingConverter());
        converters.add(new ReportReasonCodeReadingConverter());
        converters.add(new ReportReasonCodeWritingConverter());
        converters.add(new WarningTypeReadingConverter());
        converters.add(new WarningTypeWritingConverter());
        converters.add(new RestrictionTypeReadingConverter());
        converters.add(new RestrictionTypeWritingConverter());
        converters.add(new ConnectionStatusReadingConverter());
        converters.add(new ConnectionStatusWritingConverter());


        // Add JSONB converters
        converters.add(new JsonNodeToJsonWriteConverter(objectMapper));
        converters.add(new JsonToJsonNodeReadConverter(objectMapper));
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
    }
}
