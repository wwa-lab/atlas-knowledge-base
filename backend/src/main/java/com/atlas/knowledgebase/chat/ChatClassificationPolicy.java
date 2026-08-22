package com.atlas.knowledgebase.chat;

import com.atlas.knowledgebase.retrieval.RetrievalScope;
import java.util.List;

/** Fail-closed Chat classification boundary until an approved dominance taxonomy exists. */
final class ChatClassificationPolicy {

    private ChatClassificationPolicy() {}

    static String resolve(List<RetrievalScope.KnowledgeBaseSnapshot> snapshots) {
        String resolved = null;
        for (RetrievalScope.KnowledgeBaseSnapshot snapshot : snapshots) {
            String classification = snapshot.knowledgeBase().classification();
            if (classification == null || classification.isBlank()) {
                throw new ChatValidationException(
                        "CLASSIFICATION_UNAVAILABLE",
                        "Every selected knowledge base must have a known security classification.");
            }
            String normalized = classification.trim();
            if (resolved != null && !resolved.equals(normalized)) {
                throw new ChatValidationException(
                        "CLASSIFICATION_MISMATCH",
                        "Selected knowledge bases use different security classifications; "
                                + "separate them until an approved ordering policy is available.");
            }
            resolved = normalized;
        }
        if (resolved == null) {
            throw new ChatValidationException(
                    "CLASSIFICATION_UNAVAILABLE",
                    "Every selected knowledge base must have a known security classification.");
        }
        return resolved;
    }
}
