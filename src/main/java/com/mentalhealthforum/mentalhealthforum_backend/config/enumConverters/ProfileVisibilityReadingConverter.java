package com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
/**
 * R2DBC Converter for reading the String value from the PostgreSQL database back into the
 * Java ProfileVisibility enum.
 */
@ReadingConverter
public class ProfileVisibilityReadingConverter implements Converter<String, ProfileVisibility> {
    @Override
    public ProfileVisibility convert(String source) {
        return ProfileVisibility.valueOf(source);  // maps PG enum string to Java enum
    }
}
