package com.mentalhealthforum.mentalhealthforum_backend.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JsonUtils() {
        // Prevent instantiation
    }

    /**
     * Deserializes a JSON string to an object of the specified class type.
     *
     * @param json The JSON string to deserialize.
     * @param valueType The class type to deserialize into.
     * @param <T> The type of the object.
     * @return deserialized object.
     */
    public static <T> T fromJson(String json, Class<T> valueType){
        if(json == null || json.isBlank()){
            throw new IllegalArgumentException("Input JSON is null or empty");
        }
        try{
            return objectMapper.readValue(json, valueType);
        } catch(Exception e){
            log.error("Error deserialization JSON: {}", json, e);
            throw  new IllegalArgumentException("Failed to deserialize JSON: " + e.getMessage(), e);
        }
    }
    /**
     * Serializes an object to its JSON string representation.
     *
     * @param object The object to serialize.
     * @return The JSON string representation of the object.
     */
    public static String toJson(Object object){
        if(object == null){
           throw new IllegalArgumentException("Input JSON is null or empty");
        }
        try{
            return objectMapper.writeValueAsString(object);
        }catch(Exception e){
            log.error("Error serializing object: {}", object, e);
            throw new IllegalArgumentException("Failed to serialize object: " + e.getMessage(), e);
        }
    }
}
