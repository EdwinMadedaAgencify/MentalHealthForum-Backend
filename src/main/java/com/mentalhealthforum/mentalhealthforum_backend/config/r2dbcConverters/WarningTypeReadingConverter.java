package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.WarningType;

public class WarningTypeReadingConverter extends AbstractPostgresEnumReadingConverter<WarningType>{
    public WarningTypeReadingConverter() {
        super(WarningType.class);
    }
}
