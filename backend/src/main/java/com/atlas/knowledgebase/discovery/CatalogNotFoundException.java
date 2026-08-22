package com.atlas.knowledgebase.discovery;

/** Hidden or missing catalog resource. */
public final class CatalogNotFoundException extends RuntimeException {

    public CatalogNotFoundException(String logicalKbId) {
        super("Knowledge base not found: " + logicalKbId);
    }
}
