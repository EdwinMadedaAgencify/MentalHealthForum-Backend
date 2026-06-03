package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.RestrictionType;

public class RestrictionTypeReadingConverter extends AbstractPostgresEnumReadingConverter<RestrictionType>{
    public RestrictionTypeReadingConverter() {
        super(RestrictionType.class);
    }
}
