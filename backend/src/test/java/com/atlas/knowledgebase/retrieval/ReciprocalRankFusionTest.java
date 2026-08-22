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
                        "runbook:gateway-cert",
                        "https://knowledge.example/runbooks/gateway-cert",
                        "doc-shared-git",
                        "Shared Git",
                        "fixture",
                        "git-v1",
                        "{\"path\":\"runbook.md\"}",
                        1,
                        "fp-shared");
        Retriever.Hit sharedDify =
                new Retriever.Hit(
                        "runbook:gateway-cert",
                        "https://knowledge.example/runbooks/gateway-cert",
                        "doc-shared-dify",
                        "Shared Dify",
                        "fixture",
                        "git-v1",
                        "{\"document_id\":\"doc-7\"}",
                        2,
                        "fp-shared");
        Retriever.Hit onlyGit =
                hit("source:git", "https://knowledge.example/git", "doc-git", "v1", 2, "fp-git");
        Retriever.Hit onlyDify =
                hit("source:dify", "https://knowledge.example/dify", "doc-dify", "v1", 1, "fp-dify");
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
                .containsOnly("git-v1");
        assertThat(fused.stream().map(hit -> hit.hit().fingerprint()).toList())
                .containsExactly("fp-shared", "fp-dify", "fp-git");
    }

    @Test
    void usesRetrieverRankInsteadOfListPosition() {
        Retriever.Hit declaredSecond =
                hit("source:second", "https://knowledge.example/second", "doc-second", "v1", 2, "fp-second");
        Retriever.Hit declaredFirst =
                hit("source:first", "https://knowledge.example/first", "doc-first", "v1", 1, "fp-first");

        List<ReciprocalRankFusion.FusedHit> fused =
                ReciprocalRankFusion.fuse(
                        List.of(
                                new ReciprocalRankFusion.RankedList(
                                        "lkb", "bnd", "dify", List.of(declaredSecond, declaredFirst))));

        assertThat(fused.stream().map(hit -> hit.hit().fingerprint()).toList())
                .containsExactly("fp-first", "fp-second");
        assertThat(fused.getFirst().provenance().getFirst().rank()).isEqualTo(1);
    }

    @Test
    void sameFingerprintDoesNotCollapseDistinctSourcesOrVersions() {
        Retriever.Hit first =
                hit("source:a", "https://knowledge.example/a", "doc-a", "v1", 1, "same-fp");
        Retriever.Hit otherSource =
                hit("source:b", "https://knowledge.example/b", "doc-b", "v1", 1, "same-fp");
        Retriever.Hit otherVersion =
                hit("source:a", "https://knowledge.example/a", "doc-a", "v2", 1, "same-fp");

        List<ReciprocalRankFusion.FusedHit> fused =
                ReciprocalRankFusion.fuse(
                        List.of(
                                new ReciprocalRankFusion.RankedList(
                                        "lkb", "bnd_a", "dify", List.of(first)),
                                new ReciprocalRankFusion.RankedList(
                                        "lkb", "bnd_b", "git_markdown", List.of(otherSource)),
                                new ReciprocalRankFusion.RankedList(
                                        "lkb", "bnd_c", "confluence", List.of(otherVersion))));

        assertThat(fused).hasSize(3);
    }

    private static Retriever.Hit hit(
            String canonicalSourceIdentity,
            String sourceUrl,
            String documentId,
            String version,
            int rank,
            String fingerprint) {
        return new Retriever.Hit(
                canonicalSourceIdentity,
                sourceUrl,
                documentId,
                documentId,
                "fixture",
                version,
                "{}",
                rank,
                fingerprint);
    }
}
