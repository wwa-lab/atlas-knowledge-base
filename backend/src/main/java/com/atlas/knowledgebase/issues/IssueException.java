package com.atlas.knowledgebase.issues;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Typed issue-report failure with the shared API error envelope fields. */
public final class IssueException extends RuntimeException {

    private final HttpStatus status;
    private final String category;
    private final String code;
    private final String nextStep;
    private final Map<String, Object> details;

    public IssueException(
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

    public static IssueException validation(String code, String message) {
        return new IssueException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation",
                code,
                message,
                "fix_issue_report",
                Map.of());
    }

    public static IssueException notFound(String code, String message) {
        return new IssueException(
                HttpStatus.NOT_FOUND,
                "unavailable",
                code,
                message,
                "open_the_answer_again",
                Map.of());
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
