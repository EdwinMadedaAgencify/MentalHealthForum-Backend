package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;

public class ContentWarningTypeReadingConverter extends AbstractPostgresEnumReadingConverter<ContentWarningType>{
    public ContentWarningTypeReadingConverter() {
        super(ContentWarningType.class);
    }
}
