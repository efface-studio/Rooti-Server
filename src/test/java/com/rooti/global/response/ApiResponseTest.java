package com.rooti.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("ApiResponse serialises the contract the client expects (success/data/timestamp)")
    void api_response_shape() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.ok("hello"));
        JsonNode root = mapper.readTree(json);

        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.get("data").asText()).isEqualTo("hello");
        assertThat(root.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("Empty ApiResponse.ok() omits the data field (NON_NULL inclusion)")
    void empty_ok_omits_data() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.ok());
        JsonNode root = mapper.readTree(json);

        assertThat(root.has("data")).isFalse();
        assertThat(root.get("success").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("PageResponse hides Spring's Pageable internals and exposes a flat contract")
    void page_response_shape() throws Exception {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(2, 5), 14);
        var resp = PageResponse.of(page);

        JsonNode root = mapper.readTree(mapper.writeValueAsString(resp));
        assertThat(root.get("content").size()).isEqualTo(2);
        assertThat(root.get("page").asInt()).isEqualTo(2);
        assertThat(root.get("size").asInt()).isEqualTo(5);
        assertThat(root.get("totalElements").asInt()).isEqualTo(14);
        assertThat(root.get("totalPages").asInt()).isEqualTo(3);
        assertThat(root.get("hasPrevious").asBoolean()).isTrue();

        // explicitly NOT exposed
        assertThat(root.has("pageable")).isFalse();
        assertThat(root.has("sort")).isFalse();
        assertThat(root.has("empty")).isFalse();
    }

    @Test
    @DisplayName("PageResponse#map preserves pagination metadata while transforming elements")
    void page_response_map() {
        var src = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 10), 3);
        var resp = PageResponse.of(src).map(Object::toString);

        assertThat(resp.content()).containsExactly("1", "2", "3");
        assertThat(resp.totalElements()).isEqualTo(3);
        assertThat(resp.hasNext()).isFalse();
    }
}
