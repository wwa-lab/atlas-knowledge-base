package com.atlas.knowledgebase.governance;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GovernanceExceptionHandler {

    @ExceptionHandler(GovernanceValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(GovernanceValidationException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation",
                ex.code(),
                ex.getMessage(),
                "fix_governance_request");
    }

    @ExceptionHandler(GovernanceConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(GovernanceConflictException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.CONFLICT,
                "conflict",
                ex.code(),
                ex.getMessage(),
                "reload_impact_preview",
                ex.details());
    }

    @ExceptionHandler(GovernanceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> missing(GovernanceNotFoundException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.NOT_FOUND,
                "unavailable",
                ex.code(),
                ex.getMessage(),
                "reload_governance_target");
    }
}
