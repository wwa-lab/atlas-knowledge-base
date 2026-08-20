package com.atlas.knowledgebase.registry;

/**
 * Optimistic {@code config_version} mismatch on draft update or activation.
 * Mapped to HTTP 409 by {@link com.atlas.knowledgebase.registry.ConfigVersionConflictExceptionHandler}.
 */
public final class ConfigVersionConflictException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;
    private final int expectedVersion;
    private final int actualVersion;

    public ConfigVersionConflictException(
            String resourceType, String resourceId, int expectedVersion, int actualVersion) {
        super(
                "config_version conflict on "
                        + resourceType
                        + " "
                        + resourceId
                        + ": expected "
                        + expectedVersion
                        + ", actual "
                        + actualVersion);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public int expectedVersion() {
        return expectedVersion;
    }

    public int actualVersion() {
        return actualVersion;
    }
}
