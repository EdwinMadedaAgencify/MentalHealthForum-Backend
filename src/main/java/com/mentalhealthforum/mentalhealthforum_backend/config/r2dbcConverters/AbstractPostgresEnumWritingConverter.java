package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
/**
 * R2DBC Converter for writing the Java E enum to the PostgreSQL database.
 * This is crucial because PostgreSQL expects the enum value as a string literal.
 */
@WritingConverter
public class AbstractPostgresEnumWritingConverter<E extends Enum<E>> implements Converter<E, E> {
    @Override
    public E convert(@NotNull E source) {
        return source;  // identity conversion, forces Spring to treat as enum, not String
    }
}