package com.atlas.knowledgebase.registry;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RegistryExceptionHandler {

    @ExceptionHandler(RegistryForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(RegistryForbiddenException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.FORBIDDEN, "authorization", ex.code(), ex.getMessage(), ex.nextStep());
    }

    @ExceptionHandler(DraftValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(DraftValidationException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation",
                ex.code(),
                ex.getMessage(),
                "fix_draft_and_retry");
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<Map<String, Object>> missing(DraftNotFoundException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.NOT_FOUND,
                "unavailable",
                "DRAFT_NOT_FOUND",
                ex.getMessage(),
                "reload_wizard");
    }
}
