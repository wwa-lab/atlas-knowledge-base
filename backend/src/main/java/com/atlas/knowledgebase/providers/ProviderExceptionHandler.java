package com.atlas.knowledgebase.providers;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProviderExceptionHandler {

    @ExceptionHandler(InvalidProviderException.class)
    public ResponseEntity<Map<String, Object>> invalid(InvalidProviderException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.BAD_REQUEST,
                "validation",
                "INVALID_PROVIDER",
                ex.getMessage(),
                "use_github_or_confluence");
    }

    @ExceptionHandler(AlreadyConnectingException.class)
    public ResponseEntity<Map<String, Object>> conflict(AlreadyConnectingException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.CONFLICT,
                "connection",
                "ALREADY_CONNECTING",
                ex.getMessage(),
                "wait_or_use_existing_connection");
    }

    @ExceptionHandler(ProviderOauthNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> notConfigured(ProviderOauthNotConfiguredException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.SERVICE_UNAVAILABLE,
                "connection",
                "PROVIDER_OAUTH_NOT_CONFIGURED",
                ex.getMessage(),
                "complete_provider_spike");
    }
}
