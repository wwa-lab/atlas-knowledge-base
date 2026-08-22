package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.knowledgebase.adapters.Retriever;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void higherRanksScoreHigherAndDuplicateFingerprintsMergeProvenance() {
        Retriever.Hit sharedGit =
                new Retriever.Hit(
                        "doc-shared-git",
                        "Shared Git",
                        "fixture",
                        "git-v1",
                        "{\"path\":\"runbook.md\"}",
                        1,
                        "fp-shared");
        Retriever.Hit sharedDify =
                new Retriever.Hit(
                        "doc-shared-dify",
                        "Shared Dify",
                        "fixture",
                        "dify-v7",
                        "{\"document_id\":\"doc-7\"}",
                        2,
                        "fp-shared");
        Retriever.Hit onlyGit =
                new Retriever.Hit("doc-git", "Git only", "fixture", "v1", "{}", 2, "fp-git");
        Retriever.Hit onlyDify =
                new Retriever.Hit("doc-dify", "Dify only", "fixture", "v1", "{}", 1, "fp-dify");
        List<ReciprocalRankFusion.FusedHit> fused =
                ReciprocalRankFusion.fuse(
                        List.of(
                                new ReciprocalRankFusion.RankedList(
                                        "lkb_git", "bnd_git", "git_markdown", List.of(sharedGit, onlyGit)),
                                new ReciprocalRankFusion.RankedList(
                                        "lkb_dify", "bnd_dify", "dify", List.of(onlyDify, sharedDify))));
        assertThat(fused).hasSize(3);
        assertThat(fused.getFirst().hit().fingerprint()).isEqualTo("fp-shared");
        assertThat(fused.getFirst().provenance()).hasSize(2);
        assertThat(fused.getFirst().provenance().stream().map(path -> path.hit().locatorJson()).toList())
                .containsExactlyInAnyOrder(
                        "{\"path\":\"runbook.md\"}", "{\"document_id\":\"doc-7\"}");
        assertThat(fused.getFirst().provenance().stream().map(path -> path.hit().version()).toList())
                .containsExactlyInAnyOrder("git-v1", "dify-v7");
        assertThat(fused.stream().map(hit -> hit.hit().fingerprint()).toList())
                .containsExactly("fp-shared", "fp-dify", "fp-git");
    }

    @Test
    void usesRetrieverRankInsteadOfListPosition() {
        Retriever.Hit declaredSecond =
                new Retriever.Hit("doc-second", "Second", "fixture", "v1", "{}", 2, "fp-second");
        Retriever.Hit declaredFirst =
                new Retriever.Hit("doc-first", "First", "fixture", "v1", "{}", 1, "fp-first");

        List<ReciprocalRankFusion.FusedHit> fused =
                ReciprocalRankFusion.fuse(
                        List.of(
                                new ReciprocalRankFusion.RankedList(
                                        "lkb", "bnd", "dify", List.of(declaredSecond, declaredFirst))));

        assertThat(fused.stream().map(hit -> hit.hit().fingerprint()).toList())
                .containsExactly("fp-first", "fp-second");
        assertThat(fused.getFirst().provenance().getFirst().rank()).isEqualTo(1);
    }
}
