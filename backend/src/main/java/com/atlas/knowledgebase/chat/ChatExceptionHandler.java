package com.atlas.knowledgebase.chat;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ChatExceptionHandler {

    @ExceptionHandler(ChatForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(ChatForbiddenException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.FORBIDDEN, "authorization", ex.code(), ex.getMessage(), ex.nextStep());
    }

    @ExceptionHandler(ChatNotFoundException.class)
    public ResponseEntity<Map<String, Object>> missing(ChatNotFoundException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.NOT_FOUND, "unavailable", "THREAD_NOT_FOUND", ex.getMessage(), "open_chats");
    }

    @ExceptionHandler(ChatValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(ChatValidationException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation",
                ex.code(),
                ex.getMessage(),
                "fix_chat_scope");
    }

    @ExceptionHandler(ChatConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ChatConflictException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.CONFLICT, "conflict", ex.code(), ex.getMessage(), "reload_thread");
    }

    @ExceptionHandler(ChatRetrievalException.class)
    public ResponseEntity<Map<String, Object>> retrieval(ChatRetrievalException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.SERVICE_UNAVAILABLE,
                "retrieval",
                ex.code(),
                ex.getMessage(),
                ex.nextStep(),
                ex.details());
    }
}
