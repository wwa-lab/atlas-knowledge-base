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

    /** Compact immutable answer-time binding metadata required by ADR-0008. */
    public List<Map<String, String>> bindingSnapshots() {
        return knowledgeBases.stream()
                .flatMap(snapshot -> snapshot.bindings().stream())
                .map(
                        binding ->
                                Map.of(
                                        "binding_id", binding.bindingId(),
                                        "binding_role", binding.bindingRole()))
                .toList();
    }

    public Map<String, Object> configVersions() {
        Map<String, Integer> logicalKbVersions = new LinkedHashMap<>();
        Map<String, Integer> bindingVersions = new LinkedHashMap<>();
        knowledgeBases.forEach(
                snapshot -> {
                    logicalKbVersions.put(
                            snapshot.knowledgeBase().logicalKbId(),
                            snapshot.knowledgeBase().configVersion());
                    snapshot.bindings().forEach(
                            binding ->
                                    bindingVersions.put(
                                            binding.bindingId(), binding.configVersion()));
                });
        return Map.of(
                "logical_kbs", Map.copyOf(logicalKbVersions),
                "bindings", Map.copyOf(bindingVersions));
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
