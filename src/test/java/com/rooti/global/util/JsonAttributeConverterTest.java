package com.rooti.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonAttributeConverterTest {

    private final JsonAttributeConverter c = new JsonAttributeConverter();

    @Test
    void round_trip_preserves_map_contents() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("k1", "v1");
        in.put("k2", 42);
        in.put("k3", Map.of("nested", true));

        String json = c.convertToDatabaseColumn(in);
        Map<String, Object> out = c.convertToEntityAttribute(json);

        assertThat(out).containsEntry("k1", "v1");
        assertThat(out).containsEntry("k2", 42);
        assertThat(out.get("k3")).isInstanceOf(Map.class);
    }

    @Test
    void null_and_empty_attribute_serialise_to_null() {
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToDatabaseColumn(Map.of())).isNull();
    }

    @Test
    void blank_db_data_deserialises_to_null() {
        assertThat(c.convertToEntityAttribute(null)).isNull();
        assertThat(c.convertToEntityAttribute("")).isNull();
        assertThat(c.convertToEntityAttribute("   ")).isNull();
    }
}
