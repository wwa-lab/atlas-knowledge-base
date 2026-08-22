package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.knowledgebase.adapters.Retriever;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void higherRanksScoreHigherAndDuplicateFingerprintsMergeProvenance() {
        Retriever.Hit shared =
                new Retriever.Hit("doc-shared", "Shared", "fixture", "v1", "{}", 1, "fp-shared");
        Retriever.Hit onlyGit =
                new Retriever.Hit("doc-git", "Git only", "fixture", "v1", "{}", 2, "fp-git");
        Retriever.Hit onlyDify =
                new Retriever.Hit("doc-dify", "Dify only", "fixture", "v1", "{}", 1, "fp-dify");
        List<ReciprocalRankFusion.FusedHit> fused =
                ReciprocalRankFusion.fuse(
                        List.of(
                                new ReciprocalRankFusion.RankedList(
                                        "lkb_git", "bnd_git", "git_markdown", List.of(shared, onlyGit)),
                                new ReciprocalRankFusion.RankedList(
                                        "lkb_dify", "bnd_dify", "dify", List.of(onlyDify, shared))));
        assertThat(fused).hasSize(3);
        assertThat(fused.getFirst().hit().fingerprint()).isEqualTo("fp-shared");
        assertThat(fused.getFirst().provenance()).hasSize(2);
        assertThat(fused.stream().map(hit -> hit.hit().fingerprint()).toList())
                .containsExactly("fp-shared", "fp-dify", "fp-git");
    }
}
