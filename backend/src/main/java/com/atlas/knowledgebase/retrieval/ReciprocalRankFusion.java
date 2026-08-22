package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.adapters.Retriever;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion as the product ranking constraint (REQ-RAG-003 / FR-36).
 *
 * <p>Score for document {@code d} is {@code Σ 1 / (k + rank_i(d))} over retriever lists, with
 * {@code k = 60} (the common constant). Dedup is by fingerprint; every provenance path is kept.
 * Component storage, k-tuning, and cache isolation remain ADR-gated.
 */
public final class ReciprocalRankFusion {

    public static final int K = 60;

    private ReciprocalRankFusion() {}

    public static List<FusedHit> fuse(List<RankedList> lists) {
        Map<String, Acc> byFingerprint = new LinkedHashMap<>();
        for (RankedList list : lists) {
            if (list == null || list.hits() == null) {
                continue;
            }
            int position = 1;
            for (Retriever.Hit hit : list.hits()) {
                if (hit == null || hit.fingerprint() == null || hit.fingerprint().isBlank()) {
                    position++;
                    continue;
                }
                int rank = hit.rank() > 0 ? hit.rank() : position;
                double add = 1.0d / (K + rank);
                Acc acc =
                        byFingerprint.computeIfAbsent(
                                hit.fingerprint(),
                                ignored -> new Acc(0.0d, new ArrayList<>(), hit));
                acc.score += add;
                acc.paths.add(new Provenance(list.logicalKbId(), list.bindingId(), list.provider(), rank));
                position++;
            }
        }
        return byFingerprint.values().stream()
                .sorted(
                        Comparator.comparingDouble((Acc acc) -> acc.score)
                                .reversed()
                                .thenComparing(acc -> acc.canonical.fingerprint()))
                .map(
                        acc ->
                                new FusedHit(
                                        acc.canonical,
                                        List.copyOf(acc.paths),
                                        acc.paths.getFirst().logicalKbId(),
                                        acc.paths.getFirst().bindingId(),
                                        acc.paths.getFirst().provider()))
                .toList();
    }

    public record RankedList(
            String logicalKbId, String bindingId, String provider, List<Retriever.Hit> hits) {}

    public record Provenance(String logicalKbId, String bindingId, String provider, int rank) {}

    public record FusedHit(
            Retriever.Hit hit,
            List<Provenance> provenance,
            String logicalKbId,
            String bindingId,
            String provider) {}

    private static final class Acc {
        private double score;
        private final List<Provenance> paths;
        private final Retriever.Hit canonical;

        private Acc(double score, List<Provenance> paths, Retriever.Hit canonical) {
            this.score = score;
            this.paths = paths;
            this.canonical = canonical;
        }
    }
}
