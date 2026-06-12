package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ConnectionStatus;

public class ConnectionStatusReadingConverter extends AbstractPostgresEnumReadingConverter<ConnectionStatus>{
    public ConnectionStatusReadingConverter() {
        super(ConnectionStatus.class);
    }
}
