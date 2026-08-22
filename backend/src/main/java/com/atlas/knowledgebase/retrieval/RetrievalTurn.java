package com.atlas.knowledgebase.retrieval;

import java.util.List;
import java.util.Map;

/** Per-turn retrieval outcome used by Chat before model-send. */
public record RetrievalTurn(
        Map<String, Object> coverage,
        List<ReciprocalRankFusion.FusedHit> fused,
        List<Map<String, Object>> citations,
        Object conflict,
        RetrievalScope scope,
        Block block,
        String blockLogicalKbId,
        String blockBindingId) {

    public RetrievalTurn {
        coverage = coverage == null ? Map.of() : Map.copyOf(coverage);
        fused = fused == null ? List.of() : List.copyOf(fused);
        citations = citations == null ? List.of() : List.copyOf(citations);
        scope = scope == null ? new RetrievalScope(List.of()) : scope;
    }

    public enum Block {
        NONE,
        BINDING_ACCESS,
        BINDING_UNAVAILABLE,
        SECURITY,
        NO_EVIDENCE,
        UNKNOWN
    }

    public boolean blocked() {
        return block != Block.NONE;
    }
}
