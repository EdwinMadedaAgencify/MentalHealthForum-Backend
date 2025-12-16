package com.mentalhealthforum.mentalhealthforum_backend.config.r2dbcConverters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.io.IOException;

@ReadingConverter
public class JsonToJsonNodeReadConverter implements Converter<Json, JsonNode> {
    private final ObjectMapper objectMapper;

    public JsonToJsonNodeReadConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode convert(Json source) {
        try{
            return objectMapper.readTree(source.asString());
        }catch (IOException e){
            throw new RuntimeException("Failed to convert Json to JsonNode", e);
        }
    }
}
