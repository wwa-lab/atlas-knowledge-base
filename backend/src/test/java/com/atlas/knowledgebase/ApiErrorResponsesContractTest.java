package com.atlas.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiErrorResponsesContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesTheAcceptedNestedErrorEnvelopeWithoutTopLevelErrorFields() throws Exception {
        JsonNode body =
                objectMapper.readTree(
                        objectMapper.writeValueAsString(
                                ApiErrorResponses.body(
                                        "authorization",
                                        "KB_BINDING_ACCESS_MISSING",
                                        "One complete source is unavailable.",
                                        "req_contract_1",
                                        "reconnect_or_request_access",
                                        Map.of("logical_kb_id", "lkb_123", "binding_id", "bnd_456"))));

        assertThat(body.fieldNames()).toIterable().containsExactly("error");
        JsonNode error = body.path("error");
        assertThat(error.path("category").asText()).isEqualTo("authorization");
        assertThat(error.path("code").asText()).isEqualTo("KB_BINDING_ACCESS_MISSING");
        assertThat(error.path("message").asText()).isEqualTo("One complete source is unavailable.");
        assertThat(error.path("request_id").asText()).isEqualTo("req_contract_1");
        assertThat(error.path("next_step").asText()).isEqualTo("reconnect_or_request_access");
        assertThat(error.path("details").path("logical_kb_id").asText()).isEqualTo("lkb_123");
        assertThat(error.path("details").path("binding_id").asText()).isEqualTo("bnd_456");
    }

    @Test
    void generatesAnOpaqueRequestIdAndOmitsEmptyDetails() throws Exception {
        JsonNode body =
                objectMapper.readTree(
                        objectMapper.writeValueAsString(
                                ApiErrorResponses.body(
                                        "validation", "SCOPE_REQUIRED", "Select a knowledge base.", "fix_chat_scope")));

        JsonNode error = body.path("error");
        assertThat(UUID.fromString(error.path("request_id").asText())).isNotNull();
        assertThat(error.has("details")).isFalse();
        assertThat(error.fieldNames()).toIterable()
                .containsExactly("category", "code", "message", "request_id", "next_step");
    }
}
