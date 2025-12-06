package com.mentalhealthforum.mentalhealthforum_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
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
    public static <T> T jsonStringToObject(String json, Class<T> valueType) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Input JSON string is null or empty");
        }
        try {
            return objectMapper.readValue(json, valueType);
        } catch (Exception e) {
            log.error("Failed to deserialize JSON string to {}: {}", valueType.getSimpleName(), json, e);
            throw new IllegalArgumentException("Failed to deserialize JSON: " + e.getMessage(), e);
        }
    }
    /**
     * Serializes an object to its JSON string representation.
     *
     * @param object The object to serialize.
     * @return The JSON string representation of the object.
     */
    public static String objectToJsonString(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Input object is null");
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON string: {}", object, e);
            throw new IllegalArgumentException("Failed to serialize object to JSON: " + e.getMessage(), e);
        }
    }
    /**
     * Converts a JSON string into a JsonNode.
     *
     * @param json The Json string to convert.
     * @return The jsonNode representation.
     */
    public static JsonNode jsonStringToJsonNode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Input JSON string is null or empty");
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to convert JSON string to JsonNode: {}", json, e);
            throw new IllegalArgumentException("Failed to convert JSON string to JsonNode: " + e.getMessage(), e);
        }
    }
    /**
     * Converts a JsonNode into a JSON string.
     *
     * @param jsonNode The JsonNode to convert.
     * @return The JSON string representation.
     */
    public static String jsonNodeToJsonString(JsonNode jsonNode) {
        if (jsonNode == null) {
            throw new IllegalArgumentException("Input JsonNode is null");
        }
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception e) {
            log.error("Failed to convert JsonNode to JSON string: {}", jsonNode, e);
            throw new IllegalArgumentException("Failed to convert JsonNode to JSON string: " + e.getMessage(), e);
        }
    }
    /**
     * Converts a JsonNode into a Java Object of the specified type.
     *
     * @param jsonNode The JsonNode to convert.
     * @param <T> The type of the object
     * @return The deserialized object.
     */
    public static <T> T jsonNodeToObject(JsonNode jsonNode, Class<T> valueType) {
        if (jsonNode == null) {
            throw new IllegalArgumentException("Input JsonNode is null");
        }
        try {
            return objectMapper.treeToValue(jsonNode, valueType);
        } catch (Exception e) {
            log.error("Failed to convert JsonNode to {}: {}", valueType.getSimpleName(), jsonNode, e);
            throw new IllegalArgumentException("Failed to convert JsonNode to object: " + e.getMessage(), e);
        }
    }
    /**
     * Converts a Java Object into a JsonNode.
     *
     * @param object The Object to convert.
     * @return The JsonNode representation of the object.
     */
    public static JsonNode objectToJsonNode(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Input object is null");
        }
        try {
            return objectMapper.valueToTree(object);
        } catch (Exception e) {
            log.error("Failed to convert object to JsonNode: {}", object, e);
            throw new IllegalArgumentException("Failed to convert object to JsonNode: " + e.getMessage(), e);
        }
    }
}
