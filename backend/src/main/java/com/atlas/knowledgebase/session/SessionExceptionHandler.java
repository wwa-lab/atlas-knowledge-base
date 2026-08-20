package com.atlas.knowledgebase.session;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SessionExceptionHandler {

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, Object>> unauthenticated(UnauthenticatedException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.UNAUTHORIZED,
                "authentication",
                "SESSION_REQUIRED",
                ex.getMessage(),
                "start_sso");
    }

    @ExceptionHandler(CsrfMismatchException.class)
    public ResponseEntity<Map<String, Object>> csrf(CsrfMismatchException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.FORBIDDEN,
                "authorization",
                "CSRF_MISMATCH",
                ex.getMessage(),
                "fetch_csrf_and_retry");
    }

    @ExceptionHandler(SsoNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> ssoMissing(SsoNotConfiguredException ex) {
        return ApiErrorResponses.entity(
                HttpStatus.SERVICE_UNAVAILABLE,
                "connection",
                "SSO_NOT_CONFIGURED",
                ex.getMessage(),
                "configure_corporate_idp");
    }
}
