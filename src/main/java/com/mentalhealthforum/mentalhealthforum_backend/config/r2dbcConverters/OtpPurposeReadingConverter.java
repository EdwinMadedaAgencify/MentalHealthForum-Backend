package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OtpPurpose;

public class OtpPurposeReadingConverter extends AbstractPostgresEnumReadingConverter<OtpPurpose>{
    public OtpPurposeReadingConverter() {
        super(OtpPurpose.class);
    }
}

