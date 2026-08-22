package com.atlas.knowledgebase.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Shared {@code { "error": { ... } }} envelope from the API implementation guide. */
public final class ApiErrorResponses {

    private ApiErrorResponses() {}

    public static Map<String, Object> body(
            String category, String code, String message, String nextStep) {
        return body(category, code, message, nextStep, Map.of());
    }

    public static Map<String, Object> body(
            String category,
            String code,
            String message,
            String nextStep,
            Map<String, Object> details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", category);
        error.put("code", code);
        error.put("message", message);
        error.put("request_id", UUID.randomUUID().toString());
        error.put("next_step", nextStep);
        if (details != null && !details.isEmpty()) {
            error.put("details", Map.copyOf(details));
        }
        return Map.of("error", error);
    }

    public static ResponseEntity<Map<String, Object>> entity(
            HttpStatus status, String category, String code, String message, String nextStep) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(category, code, message, nextStep));
    }

    public static ResponseEntity<Map<String, Object>> entity(
            HttpStatus status,
            String category,
            String code,
            String message,
            String nextStep,
            Map<String, Object> details) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(category, code, message, nextStep, details));
    }
}
