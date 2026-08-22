package com.atlas.knowledgebase.evidence;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Typed Evidence API failure with the shared safe error envelope fields. */
public final class EvidenceException extends RuntimeException {

    private final HttpStatus status;
    private final String category;
    private final String code;
    private final String nextStep;
    private final Map<String, Object> details;

    public EvidenceException(
            HttpStatus status,
            String category,
            String code,
            String message,
            String nextStep,
            Map<String, Object> details) {
        super(message);
        this.status = status;
        this.category = category;
        this.code = code;
        this.nextStep = nextStep;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public HttpStatus status() {
        return status;
    }

    public String category() {
        return category;
    }

    public String code() {
        return code;
    }

    public String nextStep() {
        return nextStep;
    }

    public Map<String, Object> details() {
        return details;
    }
}
