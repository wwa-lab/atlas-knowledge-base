package com.atlas.knowledgebase.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps optimistic {@code config_version} failures to the API error envelope with HTTP 409.
 *
 * <p>[ASSUMPTION] category {@code conflict} and code {@code CONFIG_VERSION_CONFLICT} until the
 * API guide names a dedicated concurrency category.
 */
@RestControllerAdvice
public class ConfigVersionConflictExceptionHandler {

    @ExceptionHandler(ConfigVersionConflictException.class)
    public ResponseEntity<Map<String, Object>> handle(ConfigVersionConflictException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("resource_type", ex.resourceType());
        details.put("resource_id", ex.resourceId());
        details.put("expected_config_version", ex.expectedVersion());
        details.put("actual_config_version", ex.actualVersion());

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", "conflict");
        error.put("code", "CONFIG_VERSION_CONFLICT");
        error.put("message", "The knowledge-base configuration changed; reload and retry.");
        error.put("request_id", UUID.randomUUID().toString());
        error.put("next_step", "reload_draft_and_retry");
        error.put("details", details);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", error));
    }
}
