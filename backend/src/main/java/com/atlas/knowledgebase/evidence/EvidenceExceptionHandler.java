package com.atlas.knowledgebase.evidence;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class EvidenceExceptionHandler {

    @ExceptionHandler(EvidenceException.class)
    public ResponseEntity<Map<String, Object>> evidence(EvidenceException exception) {
        return ApiErrorResponses.entity(
                exception.status(),
                exception.category(),
                exception.code(),
                exception.getMessage(),
                exception.nextStep(),
                exception.details());
    }
}
