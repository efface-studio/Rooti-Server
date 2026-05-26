package com.rooti.global.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

/**
 * JPA {@code AttributeConverter} that round-trips {@code Map<String, Object>} through PostgreSQL
 * JSONB columns.
 *
 * <p>Used by entities that mirror Django's loose "context" / "process" JSONField semantics —
 * notably {@code JobStandard.context}, {@code JobProcess.context}, {@code JobWorker.context},
 * {@code Company.template_data}, {@code WorkProcessRecord.process}.
 *
 * <p>Persisting a {@code null} map produces a SQL NULL (not the literal JSON string {@code "null"})
 * which matches PostgreSQL's expectation for nullable JSONB columns.
 */
@Converter
public class JsonAttributeConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON attribute", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize JSON attribute", e);
        }
    }
}
