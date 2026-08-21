package com.atlas.knowledgebase.registry;

/** Draft logical KB was not found for this owner. */
public final class DraftNotFoundException extends RuntimeException {

    public DraftNotFoundException(String logicalKbId) {
        super("Draft knowledge base not found: " + logicalKbId);
    }
}
