package com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * R2DBC Converter for reading the String value from the PostgreSQL database back into the
 * Java E enum.
 */
@ReadingConverter
public abstract class AbstractPostgresEnumReadingConverter <E extends Enum<E>> implements Converter<String, E> {
    
    private final Class<E> enumClass;

    protected AbstractPostgresEnumReadingConverter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public E convert(@NotNull String source) {
        return E.valueOf(enumClass,source);  // maps PG enum string to Java enum
    }
}
