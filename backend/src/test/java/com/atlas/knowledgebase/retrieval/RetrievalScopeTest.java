package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalScopeTest {

    @Test
    void snapshotDefensivelyPreservesExactVersionsAndBindings() {
        Instant now = Instant.parse("2026-08-22T04:00:00Z");
        LogicalKnowledgeBaseRecord kb =
                new LogicalKnowledgeBaseRecord(
                        "lkb_snapshot",
                        "Snapshot",
                        "desc",
                        "usr_owner",
                        "private",
                        "support",
                        "internal",
                        true,
                        "chat_ready",
                        "active",
                        "healthy",
                        7,
                        null,
                        false,
                        null,
                        now,
                        now,
                        now);
        List<BindingRecord> bindings = new ArrayList<>();
        bindings.add(binding("bnd_snapshot", now));

        RetrievalScope scope =
                new RetrievalScope(List.of(new RetrievalScope.KnowledgeBaseSnapshot(kb, bindings)));
        bindings.clear();

        assertThat(scope.logicalKbIds()).containsExactly("lkb_snapshot");
        assertThat(scope.bindingIds()).containsExactly("bnd_snapshot");
        assertThat(scope.configVersions()).containsEntry("lkb_snapshot", 7);
        assertThat(scope.knowledgeBases().getFirst().bindings()).hasSize(1);
    }

    private static BindingRecord binding(String bindingId, Instant now) {
        return new BindingRecord(
                bindingId,
                "lkb_snapshot",
                "dify",
                "{}",
                "canonical",
                "delegated_user",
                "healthy",
                true,
                false,
                true,
                null,
                "{}",
                "owner@example.com",
                null,
                3,
                now,
                now);
    }
}
