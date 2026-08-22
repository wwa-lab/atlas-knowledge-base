package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable per-turn registry snapshot shared by retrieval dispatch and answer persistence. */
public record RetrievalScope(List<KnowledgeBaseSnapshot> knowledgeBases) {

    public RetrievalScope {
        knowledgeBases = knowledgeBases == null ? List.of() : List.copyOf(knowledgeBases);
    }

    public List<String> logicalKbIds() {
        return knowledgeBases.stream()
                .map(snapshot -> snapshot.knowledgeBase().logicalKbId())
                .toList();
    }

    public List<String> bindingIds() {
        return knowledgeBases.stream()
                .flatMap(snapshot -> snapshot.bindings().stream())
                .map(BindingRecord::bindingId)
                .toList();
    }

    public Map<String, Integer> configVersions() {
        Map<String, Integer> versions = new LinkedHashMap<>();
        knowledgeBases.forEach(
                snapshot ->
                        versions.put(
                                snapshot.knowledgeBase().logicalKbId(),
                                snapshot.knowledgeBase().configVersion()));
        return Map.copyOf(versions);
    }

    public record KnowledgeBaseSnapshot(
            LogicalKnowledgeBaseRecord knowledgeBase, List<BindingRecord> bindings) {
        public KnowledgeBaseSnapshot {
            if (knowledgeBase == null) {
                throw new IllegalArgumentException("knowledgeBase is required");
            }
            bindings = bindings == null ? List.of() : List.copyOf(bindings);
        }
    }
}
