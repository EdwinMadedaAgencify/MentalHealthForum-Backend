package com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
/**
 * R2DBC Converter for reading the String value from the PostgreSQL database back into the
 * Java SupportRole enum.
 */
@ReadingConverter
public class SupportRoleReadingConverter implements Converter<String, SupportRole> {
    @Override
    public SupportRole convert(String source) {
        return SupportRole.valueOf(source);  // maps PG enum string to Java enum
    }
}

