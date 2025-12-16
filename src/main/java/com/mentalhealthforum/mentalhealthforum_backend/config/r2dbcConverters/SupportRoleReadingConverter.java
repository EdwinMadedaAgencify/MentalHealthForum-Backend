package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;

public class SupportRoleReadingConverter extends AbstractPostgresEnumReadingConverter<SupportRole>{
    public SupportRoleReadingConverter() {
        super(SupportRole.class);
    }
}
