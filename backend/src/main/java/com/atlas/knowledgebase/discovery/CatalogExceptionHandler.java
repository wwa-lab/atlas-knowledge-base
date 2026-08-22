package com.atlas.knowledgebase.discovery;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(CatalogForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(CatalogForbiddenException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.FORBIDDEN, "authorization", ex.code(), ex.getMessage(), ex.nextStep());
    }

    @ExceptionHandler(CatalogNotFoundException.class)
    public ResponseEntity<Map<String, Object>> missing(CatalogNotFoundException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.NOT_FOUND,
                "unavailable",
                "KB_NOT_FOUND",
                ex.getMessage(),
                "open_catalog");
    }

    @ExceptionHandler(BrowseMismatchException.class)
    public ResponseEntity<Map<String, Object>> mismatch(BrowseMismatchException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.CONFLICT,
                "conflict",
                ex.code(),
                ex.getMessage(),
                "open_authorized_git_browse");
    }

    @ExceptionHandler(CatalogValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(CatalogValidationException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation",
                ex.code(),
                ex.getMessage(),
                "fix_browse_request");
    }
}
