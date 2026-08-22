package com.atlas.knowledgebase.evidence;

import com.atlas.knowledgebase.adapters.Retriever;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.retrieval.ReciprocalRankFusion;
import com.atlas.knowledgebase.retrieval.RetrievalScope;
import com.atlas.knowledgebase.retrieval.RetrievalTurn;
import com.atlas.knowledgebase.session.AtlasUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Materializes the answer-time citation snapshot from every RRF provenance path. */
@Component
public class CitationAssembler {

    private static final int MAX_CITATIONS = 9_999;

    private final AtlasUserRepository users;
    private final EvidenceLocatorValidator locatorValidator;

    public CitationAssembler(
            AtlasUserRepository users, EvidenceLocatorValidator locatorValidator) {
        this.users = users;
        this.locatorValidator = locatorValidator;
    }

    public Assembly assemble(String messageId, RetrievalTurn turn, Instant verifiedAt) {
        requireText(messageId, 64, "message_id");
        if (turn == null || turn.scope() == null) {
            throw incomplete("retrieval scope");
        }
        if (verifiedAt == null) {
            throw incomplete("atlas_verified_at");
        }
        List<ReciprocalRankFusion.Provenance> paths =
                turn.fused().stream()
                        .flatMap(fused -> fused.provenance().stream())
                        .toList();
        if (paths.isEmpty()) {
            throw incomplete("provenance");
        }
        if (paths.size() > MAX_CITATIONS) {
            throw incomplete("provenance count");
        }

        List<CitationRecord> citations = new ArrayList<>(paths.size());
        List<Map<String, Object>> summaries = new ArrayList<>(paths.size());
        for (int index = 0; index < paths.size(); index++) {
            ReciprocalRankFusion.Provenance path = paths.get(index);
            CitationRecord citation = materialize(messageId, path, turn.scope(), verifiedAt, index + 1);
            citations.add(citation);
            summaries.add(summary(citation));
        }
        return new Assembly(citations, summaries);
    }

    /**
     * Removes invalid provenance paths before model dispatch. A fused item survives only when at
     * least one of its paths has complete persistable metadata; its representative hit is replaced
     * with the first valid path so the model never receives an invalid representative.
     */
    public RetrievalTurn filterValidCandidates(RetrievalTurn turn) {
        if (turn == null || turn.scope() == null) {
            throw incomplete("retrieval scope");
        }
        List<ReciprocalRankFusion.FusedHit> filtered = new ArrayList<>();
        List<Map<String, Object>> omissions = new ArrayList<>();
        int ordinal = 1;
        for (ReciprocalRankFusion.FusedHit fused : turn.fused()) {
            List<ReciprocalRankFusion.Provenance> validPaths = new ArrayList<>();
            for (ReciprocalRankFusion.Provenance path : fused.provenance()) {
                try {
                    materialize("validation", path, turn.scope(), Instant.EPOCH, ordinal);
                    validPaths.add(path);
                    ordinal++;
                } catch (CitationMetadataIncompleteException invalid) {
                    omissions.add(omission(path));
                }
            }
            if (!validPaths.isEmpty()) {
                ReciprocalRankFusion.Provenance representative = validPaths.getFirst();
                filtered.add(
                        new ReciprocalRankFusion.FusedHit(
                                representative.hit(),
                                validPaths,
                                representative.logicalKbId(),
                                representative.bindingId(),
                                representative.provider()));
            }
        }

        Map<String, Object> coverage = new LinkedHashMap<>(turn.coverage());
        if (!omissions.isEmpty()) {
            coverage.put("partial_coverage", true);
            coverage.put("item_omitted", List.copyOf(omissions));
        }
        return new RetrievalTurn(
                coverage,
                filtered,
                turn.citations(),
                turn.conflict(),
                turn.scope(),
                turn.block(),
                turn.blockLogicalKbId(),
                turn.blockBindingId());
    }

    /** Compact answer-time role snapshot persisted on the assistant message. */
    public List<Map<String, String>> bindingRoleSnapshot(RetrievalScope scope) {
        if (scope == null) {
            throw incomplete("retrieval scope");
        }
        List<Map<String, String>> snapshot = new ArrayList<>();
        for (RetrievalScope.KnowledgeBaseSnapshot knowledgeBase : scope.knowledgeBases()) {
            for (BindingRecord binding : knowledgeBase.bindings()) {
                requireText(binding.bindingId(), 64, "binding_id");
                requireText(binding.bindingRole(), 32, "binding_role");
                snapshot.add(
                        Map.of(
                                "binding_id", binding.bindingId(),
                                "binding_role", binding.bindingRole()));
            }
        }
        return List.copyOf(snapshot);
    }

    private CitationRecord materialize(
            String messageId,
            ReciprocalRankFusion.Provenance path,
            RetrievalScope scope,
            Instant verifiedAt,
            int ordinal) {
        if (path == null || path.hit() == null) {
            throw incomplete("provenance hit");
        }
        Retriever.Hit hit = path.hit();
        LogicalKnowledgeBaseRecord kb = findKnowledgeBase(scope, path.logicalKbId());
        BindingRecord binding = findBinding(scope, path.logicalKbId(), path.bindingId());
        requireText(path.logicalKbId(), 64, "logical_kb_id");
        requireText(path.bindingId(), 64, "binding_id");
        requireText(path.provider(), 32, "provider");
        if (!path.provider().equals(binding.providerProfile())) {
            throw incomplete("provider/binding mismatch");
        }
        requireText(hit.version(), 128, "version_label");
        requireText(hit.excerpt(), Integer.MAX_VALUE, "excerpt");
        requireText(hit.title(), 512, "document_title");
        requireText(kb.classification(), 128, "classification");
        requireText(binding.bindingRole(), 32, "binding_role");
        validateLocator(path.provider(), hit.locatorJson());
        String owner = owner(kb);
        String citationId = citationId(messageId, path, ordinal);
        return new CitationRecord(
                citationId,
                messageId,
                path.logicalKbId(),
                path.bindingId(),
                path.provider(),
                hit.locatorJson(),
                hit.version(),
                hit.excerpt(),
                hit.title(),
                owner,
                kb.classification(),
                null,
                verifiedAt,
                "ok");
    }

    private LogicalKnowledgeBaseRecord findKnowledgeBase(RetrievalScope scope, String logicalKbId) {
        return scope.knowledgeBases().stream()
                .map(RetrievalScope.KnowledgeBaseSnapshot::knowledgeBase)
                .filter(kb -> kb.logicalKbId().equals(logicalKbId))
                .findFirst()
                .orElseThrow(() -> incomplete("logical knowledge-base snapshot"));
    }

    private BindingRecord findBinding(
            RetrievalScope scope, String logicalKbId, String bindingId) {
        return scope.knowledgeBases().stream()
                .filter(snapshot -> snapshot.knowledgeBase().logicalKbId().equals(logicalKbId))
                .flatMap(snapshot -> snapshot.bindings().stream())
                .filter(binding -> binding.bindingId().equals(bindingId))
                .findFirst()
                .orElseThrow(() -> incomplete("binding snapshot"));
    }

    private String owner(LogicalKnowledgeBaseRecord kb) {
        requireText(kb.ownerUserId(), 64, "owner_user_id");
        String displayName =
                users.findById(kb.ownerUserId())
                        .map(user -> user.displayName())
                        .filter(name -> name != null && !name.isBlank())
                        .orElse(kb.ownerUserId());
        requireText(displayName, 255, "owner");
        return displayName;
    }

    private void validateLocator(String provider, String locatorJson) {
        requireText(locatorJson, Integer.MAX_VALUE, "locator");
        if (locatorJson.getBytes(StandardCharsets.UTF_8).length > 16_384) {
            throw incomplete("locator");
        }
        try {
            locatorValidator.validate(provider, locatorJson);
        } catch (EvidenceLocatorValidator.InvalidLocatorException invalid) {
            throw incomplete("locator");
        }
    }

    private static Map<String, Object> summary(CitationRecord citation) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("citation_id", citation.citationId());
        summary.put("logical_kb_id", citation.logicalKbId());
        summary.put("binding_id", citation.bindingId());
        summary.put("provider", citation.provider());
        summary.put("title", citation.documentTitle());
        return Map.copyOf(summary);
    }

    private static Map<String, Object> omission(ReciprocalRankFusion.Provenance path) {
        Map<String, Object> omission = new LinkedHashMap<>();
        if (path != null) {
            putIfPresent(omission, "logical_kb_id", path.logicalKbId());
            putIfPresent(omission, "binding_id", path.bindingId());
            putIfPresent(omission, "provider", path.provider());
        }
        omission.put("reason", "citation_metadata_incomplete");
        return Map.copyOf(omission);
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String citationId(
            String messageId, ReciprocalRankFusion.Provenance path, int ordinal) {
        Retriever.Hit hit = path.hit();
        String identity =
                String.join(
                        "\u0000",
                        messageId,
                        Integer.toString(ordinal),
                        path.logicalKbId(),
                        path.bindingId(),
                        path.provider(),
                        nullSafe(hit.canonicalSourceIdentity()),
                        nullSafe(hit.sourceUrl()),
                        nullSafe(hit.documentId()),
                        nullSafe(hit.version()),
                        nullSafe(hit.locatorJson()),
                        nullSafe(hit.fingerprint()));
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(identity.getBytes(StandardCharsets.UTF_8));
            return "cit_%04d_%s"
                    .formatted(ordinal, HexFormat.of().formatHex(digest).substring(0, 55));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw incomplete(field);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static CitationMetadataIncompleteException incomplete(String field) {
        return new CitationMetadataIncompleteException(field);
    }

    public record Assembly(List<CitationRecord> citations, List<Map<String, Object>> summaries) {
        public Assembly {
            citations = citations == null ? List.of() : List.copyOf(citations);
            summaries = summaries == null ? List.of() : List.copyOf(summaries);
        }
    }

    /** Explicit completion invariant error; the service transaction must roll back on this type. */
    public static final class CitationMetadataIncompleteException extends RuntimeException {
        public CitationMetadataIncompleteException(String field) {
            super("CITATION_METADATA_INCOMPLETE: " + field + " is missing or invalid");
        }

        public String code() {
            return "CITATION_METADATA_INCOMPLETE";
        }
    }
}
