package com.atlas.knowledgebase.retrieval;

import java.util.List;
import java.util.Map;

/** Per-turn retrieval outcome used by Chat before model-send. */
public record RetrievalTurn(
        Map<String, Object> coverage,
        List<ReciprocalRankFusion.FusedHit> fused,
        List<Map<String, Object>> citations,
        Object conflict,
        Block block,
        String blockLogicalKbId,
        String blockBindingId) {

    public enum Block {
        NONE,
        BINDING_ACCESS,
        SECURITY,
        NO_EVIDENCE
    }

    public boolean blocked() {
        return block != Block.NONE;
    }
}
