package com.atlas.knowledgebase.issues;

import java.util.Locale;

/** User-visible issue categories accepted by the issue-report endpoint. */
public enum IssueCategory {
    CONTENT("content"),
    CITATION("citation"),
    RETRIEVAL("retrieval"),
    PERMISSION_CONNECTION("permission_connection"),
    MODEL("model"),
    SYSTEM_SECURITY("system_security");

    private final String wireName;

    IssueCategory(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static IssueCategory parse(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (IssueCategory category : values()) {
            if (category.wireName.equals(normalized)) {
                return category;
            }
        }
        throw IssueException.validation(
                "CATEGORY_INVALID",
                "category must be content, citation, retrieval, permission_connection, model, or system_security.");
    }
}
