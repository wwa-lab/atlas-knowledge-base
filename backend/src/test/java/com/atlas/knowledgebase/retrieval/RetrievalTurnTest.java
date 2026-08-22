package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievalTurnTest {

    @Test
    void defensivelyCopiesCollectionsAtTheAsyncBoundary() {
        List<String> successful = new ArrayList<>(List.of("bnd_1"));
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("successful", List.copyOf(successful));
        RetrievalTurn turn =
                new RetrievalTurn(
                        coverage,
                        List.of(),
                        List.of(),
                        null,
                        RetrievalTurn.Block.NONE,
                        null,
                        null);

        coverage.put("failed", List.of("bnd_2"));

        assertThat(turn.coverage()).doesNotContainKey("failed");
        assertThatThrownBy(() -> turn.coverage().put("timed_out", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
