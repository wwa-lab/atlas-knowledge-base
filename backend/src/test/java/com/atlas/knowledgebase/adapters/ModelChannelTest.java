package com.atlas.knowledgebase.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelChannelTest {

    @Test
    void requestDefensivelyCopiesEvidenceIdentifiers() {
        List<String> evidenceIds = new ArrayList<>(List.of("ev_1"));

        ModelChannel.Request request = new ModelChannel.Request("req_1", "q", "usr_1", evidenceIds);
        evidenceIds.add("ev_2");

        assertThat(request.evidenceIds()).containsExactly("ev_1");
        assertThatThrownBy(() -> request.evidenceIds().add("ev_3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
