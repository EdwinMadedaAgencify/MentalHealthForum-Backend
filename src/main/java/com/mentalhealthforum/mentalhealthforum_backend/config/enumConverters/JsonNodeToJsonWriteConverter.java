package com.mentalhealthforum.mentalhealthforum_backend.config.enumConverters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.io.IOException;

@WritingConverter
public class JsonNodeToJsonWriteConverter implements Converter<JsonNode, Json> {
    private final ObjectMapper objectMapper;

    public JsonNodeToJsonWriteConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Json convert(@NotNull JsonNode source) {
        try{
            return Json.of(objectMapper.writeValueAsString(source));
        }catch(IOException e){
            throw new RuntimeException("Failed to convert JsonNode to Json", e);
        }
    }
}
