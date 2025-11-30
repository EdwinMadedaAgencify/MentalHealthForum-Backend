package com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
/**
 * R2DBC Converter for writing the Java SupportRole enum to the PostgreSQL database.
 * This is crucial because PostgreSQL expects the enum value as a string literal.
 */
@WritingConverter
public class SupportRoleWritingConverter implements Converter<SupportRole, SupportRole> {
    @Override
    public SupportRole convert(SupportRole source) {
        return source;  // identity conversion, forces Spring to treat as enum, not String
    }
}