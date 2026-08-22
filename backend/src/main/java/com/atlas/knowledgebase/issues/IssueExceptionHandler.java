package com.atlas.knowledgebase.issues;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IssueExceptionHandler {

    @ExceptionHandler(IssueException.class)
    public ResponseEntity<Map<String, Object>> issue(IssueException exception) {
        return ApiErrorResponses.entity(
                exception.status(),
                exception.category(),
                exception.code(),
                exception.getMessage(),
                exception.nextStep(),
                exception.details());
    }
}
