package com.atlas.knowledgebase.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatClassificationPropertiesTest {

    @Test
    void rejectsAnEmptyApprovedClassificationBoundaryAtStartup() {
        ChatClassificationProperties properties = new ChatClassificationProperties();
        properties.setApprovedValues(Set.of(" "));

        assertThatThrownBy(() -> new ChatClassificationPolicy(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved classification");
    }
}
