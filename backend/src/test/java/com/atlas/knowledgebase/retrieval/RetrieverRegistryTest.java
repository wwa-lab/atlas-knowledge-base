package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlas.knowledgebase.adapters.Retriever;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetrieverRegistryTest {

    @Test
    void resolvesOneExplicitHandlerPerProvider() {
        Retriever dify = retriever("dify");
        Retriever git = retriever("git_markdown");
        RetrieverRegistry registry = new RetrieverRegistry(java.util.List.of(dify, git));

        assertThat(registry.find("dify")).contains(dify);
        assertThat(registry.find("git_markdown")).contains(git);
        assertThat(registry.find("confluence")).isEmpty();
    }

    @Test
    void rejectsDuplicateActiveHandlersInsteadOfDependingOnBeanOrder() {
        Retriever stub = retriever("dify");
        Retriever real = retriever("dify");

        assertThatThrownBy(() -> new RetrieverRegistry(java.util.List.of(stub, real)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dify");
    }

    private static Retriever retriever(String provider) {
        return new Retriever() {
            @Override
            public Set<String> providerProfiles() {
                return Set.of(provider);
            }

            @Override
            public Result retrieve(Request request) {
                return Result.success(java.util.List.of(), java.util.List.of());
            }
        };
    }
}
