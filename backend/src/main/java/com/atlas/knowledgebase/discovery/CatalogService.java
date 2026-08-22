package com.atlas.knowledgebase.discovery;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.GitBrowse;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.ContentAuditResultRecord;
import com.atlas.knowledgebase.registry.ContentAuditResultRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.AtlasUserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Authorization-aware catalog/detail and Git Browse. [ASSUMPTION] Without a membership table,
 * Owner and Atlas Admin are authorized; other users see Catalog request-path only or hidden
 * Private. Ordinary users see Active only (FR-27); Owner/Admin also see Suspended.
 */
@Service
public class CatalogService {

    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final ContentAuditResultRepository audits;
    private final AtlasUserRepository users;
    private final GitBrowse gitBrowse;
    private final KbAccessService access;

    public CatalogService(
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            ContentAuditResultRepository audits,
            AtlasUserRepository users,
            GitBrowse gitBrowse,
            KbAccessService access) {
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.audits = audits;
        this.users = users;
        this.gitBrowse = gitBrowse;
        this.access = access;
    }

    public Map<String, Object> list(AtlasUserRecord user, CatalogQuery query) {
        List<Map<String, Object>> items = new ArrayList<>();
        boolean afterCursor = query.cursor() == null || query.cursor().isBlank();
        int limit = query.limit() == null || query.limit() < 1 ? 50 : Math.min(query.limit(), 100);
        String next = null;
        for (LogicalKnowledgeBaseRecord kb : knowledgeBases.findPublished()) {
            if (!visible(user, kb)) {
                continue;
            }
            if (!matches(kb, query)) {
                continue;
            }
            if (!afterCursor) {
                if (kb.logicalKbId().equals(query.cursor())) {
                    afterCursor = true;
                }
                continue;
            }
            if (items.size() >= limit) {
                // The cursor is the last item included in this page. The next request resumes
                // after that id, so the first item that did not fit is not skipped.
                next = (String) items.get(items.size() - 1).get("logical_kb_id");
                break;
            }
            items.add(listProjection(user, kb));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("next_cursor", next);
        return body;
    }

    public Map<String, Object> detail(AtlasUserRecord user, String logicalKbId) {
        LogicalKnowledgeBaseRecord kb = published(logicalKbId);
        if (!visible(user, kb)) {
            throw new CatalogNotFoundException(logicalKbId);
        }
        boolean authorized = authorized(user, kb);
        Map<String, Object> body = listProjection(user, kb);
        if (!authorized) {
            return body;
        }
        List<BindingRecord> bindingRows = bindings.findByLogicalKbId(logicalKbId);
        List<Map<String, Object>> sources = new ArrayList<>();
        List<Map<String, Object>> bindingSummary = new ArrayList<>();
        for (BindingRecord binding : bindingRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("binding_id", binding.bindingId());
            row.put("provider_profile", binding.providerProfile());
            row.put("role", binding.bindingRole());
            bindingSummary.add(row);
            sources.add(sourceProjection(kb, binding));
        }
        body.put("bindings", bindingSummary);
        body.put("overview", overview(kb));
        body.put("sources", sources);
        body.put("content", contentSection(bindingRows));
        Map<String, Object> access = accessObject(true);
        access.put("discoverability", kb.discoverability());
        body.put("access", access);
        body.put("health_detail", Map.of("status", kb.health()));
        body.put("audit_summary", auditSummary(logicalKbId));
        body.put(
                "chat_start_allowed",
                "chat_ready".equals(kb.capability()) && kb.modelEligible());
        return body;
    }

    public Map<String, Object> tree(AtlasUserRecord user, String logicalKbId) {
        BindingRecord git = requireAuthorizedGit(user, logicalKbId);
        GitBrowse.Tree tree = gitBrowse.tree(browseRequest(git));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logical_kb_id", logicalKbId);
        body.put("binding_id", tree.bindingId());
        body.put(
                "entries",
                tree.entries().stream()
                        .map(
                                entry -> {
                                    Map<String, Object> row = new LinkedHashMap<>();
                                    row.put("path", entry.path());
                                    row.put("type", entry.type());
                                    return row;
                                })
                        .toList());
        body.put("original_url", tree.originalUrl());
        return body;
    }

    public Map<String, Object> preview(AtlasUserRecord user, String logicalKbId, String path) {
        if (path == null || path.isBlank()) {
            throw new CatalogValidationException("PATH_REQUIRED", "Browse preview requires path.");
        }
        BindingRecord git = requireAuthorizedGit(user, logicalKbId);
        boolean known =
                gitBrowse.tree(browseRequest(git)).entries().stream()
                        .anyMatch(entry -> path.equals(entry.path()) && "file".equals(entry.type()));
        if (!known) {
            throw new CatalogValidationException("PATH_NOT_FOUND", "No such browse file: " + path);
        }
        GitBrowse.Preview preview = gitBrowse.preview(browseRequest(git), path);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logical_kb_id", logicalKbId);
        body.put("binding_id", preview.bindingId());
        body.put("path", preview.path());
        body.put("markdown", preview.markdown());
        body.put("original_url", preview.originalUrl());
        return body;
    }

    private BindingRecord requireAuthorizedGit(AtlasUserRecord user, String logicalKbId) {
        LogicalKnowledgeBaseRecord kb = published(logicalKbId);
        if (!visible(user, kb)) {
            throw new CatalogNotFoundException(logicalKbId);
        }
        if (!authorized(user, kb)) {
            throw new CatalogForbiddenException(
                    "BROWSE_FORBIDDEN",
                    "Browse requires authorization for this knowledge base.",
                    "request_access");
        }
        return bindings.findByLogicalKbId(logicalKbId).stream()
                .filter(binding -> "git_markdown".equals(binding.providerProfile()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new BrowseMismatchException(
                                        "GIT_BROWSE_REQUIRED",
                                        "Browse tree/preview applies to Git Markdown knowledge bases."));
    }

    private LogicalKnowledgeBaseRecord published(String logicalKbId) {
        LogicalKnowledgeBaseRecord kb =
                knowledgeBases
                        .findById(logicalKbId)
                        .orElseThrow(() -> new CatalogNotFoundException(logicalKbId));
        if (!"active".equals(kb.lifecycle()) && !"suspended".equals(kb.lifecycle())) {
            throw new CatalogNotFoundException(logicalKbId);
        }
        return kb;
    }

    private boolean authorized(AtlasUserRecord user, LogicalKnowledgeBaseRecord kb) {
        return access.authorized(user, kb);
    }

    private boolean visible(AtlasUserRecord user, LogicalKnowledgeBaseRecord kb) {
        return access.visible(user, kb);
    }

    private boolean matches(LogicalKnowledgeBaseRecord kb, CatalogQuery query) {
        if (query.q() != null && !query.q().isBlank()) {
            String needle = query.q().toLowerCase(Locale.ROOT);
            String name = kb.name() == null ? "" : kb.name().toLowerCase(Locale.ROOT);
            String description = kb.description() == null ? "" : kb.description().toLowerCase(Locale.ROOT);
            String owner = ownerLabel(kb);
            String ownerHay = owner == null ? "" : owner.toLowerCase(Locale.ROOT);
            String ownerId = kb.ownerUserId() == null ? "" : kb.ownerUserId().toLowerCase(Locale.ROOT);
            if (!name.contains(needle)
                    && !description.contains(needle)
                    && !ownerHay.contains(needle)
                    && !ownerId.contains(needle)) {
                return false;
            }
        }
        if (query.capability() != null
                && !query.capability().isBlank()
                && !query.capability().equals(kb.capability())) {
            return false;
        }
        if (query.lifecycle() != null
                && !query.lifecycle().isBlank()
                && !query.lifecycle().equals(kb.lifecycle())) {
            return false;
        }
        if (query.health() != null && !query.health().isBlank() && !query.health().equals(kb.health())) {
            return false;
        }
        if (query.freshness() != null
                && !query.freshness().isBlank()
                && !query.freshness().equals(freshnessStatus())) {
            return false;
        }
        if (query.owner() != null && !query.owner().isBlank()) {
            String owner = ownerLabel(kb);
            if (!query.owner().equals(kb.ownerUserId()) && !query.owner().equalsIgnoreCase(owner)) {
                return false;
            }
        }
        if (query.provider() != null && !query.provider().isBlank()) {
            boolean has =
                    bindings.findByLogicalKbId(kb.logicalKbId()).stream()
                            .anyMatch(binding -> query.provider().equals(binding.providerProfile()));
            if (!has) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> listProjection(AtlasUserRecord user, LogicalKnowledgeBaseRecord kb) {
        boolean authorized = authorized(user, kb);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logical_kb_id", kb.logicalKbId());
        body.put("name", kb.name());
        body.put("owner", ownerLabel(kb));
        body.put("capability", kb.capability());
        body.put("lifecycle", kb.lifecycle());
        body.put("health", kb.health());
        Map<String, Object> access = accessObject(authorized);
        if (!authorized) {
            access.put("access_request_url", kb.accessRequestUrl());
            body.put("access", access);
            return body;
        }
        body.put("description", kb.description());
        body.put("source_badges", sourceBadges(kb.logicalKbId()));
        Map<String, Object> freshness = new LinkedHashMap<>();
        freshness.put("status", freshnessStatus());
        freshness.put("source_updated_at", kb.updatedAt() == null ? null : kb.updatedAt().toString());
        body.put("freshness", freshness);
        body.put("atlas_verified_at", kb.activatedAt() == null ? null : kb.activatedAt().toString());
        body.put("scale", scaleByProvider(kb.logicalKbId()));
        body.put("access", access);
        body.put("model_eligible", kb.modelEligible());
        if ("browse_only".equals(kb.capability()) || !kb.modelEligible()) {
            body.put(
                    "chat_disabled_reason",
                    kb.modelEligible()
                            ? "Browse-only knowledge bases cannot enter Chat."
                            : "Model-ineligible knowledge bases cannot enter Chat.");
        }
        return body;
    }

    private Map<String, Object> overview(LogicalKnowledgeBaseRecord kb) {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("name", kb.name());
        overview.put("description", kb.description());
        overview.put("capability", kb.capability());
        overview.put("lifecycle", kb.lifecycle());
        overview.put("health", kb.health());
        overview.put("purpose", kb.purpose());
        overview.put("classification", kb.classification());
        overview.put("model_eligible", kb.modelEligible());
        return overview;
    }

    private Map<String, Object> sourceProjection(LogicalKnowledgeBaseRecord kb, BindingRecord binding) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("binding_id", binding.bindingId());
        source.put("provider_profile", binding.providerProfile());
        source.put("role", binding.bindingRole());
        source.put("health", binding.health());
        source.put("enabled", binding.enabled());
        source.put("connection_state", binding.health());
        source.put("updated_at", binding.updatedAt() == null ? null : binding.updatedAt().toString());
        source.put("atlas_verified_at", kb.activatedAt() == null ? null : kb.activatedAt().toString());
        source.put("scale", scaleForBinding(binding));
        return source;
    }

    private Map<String, Object> contentSection(List<BindingRecord> bindingRows) {
        Map<String, Object> content = new LinkedHashMap<>();
        boolean git =
                bindingRows.stream().anyMatch(binding -> "git_markdown".equals(binding.providerProfile()));
        content.put("browse_available", git);
        content.put("browse_kind", git ? "git_tree" : null);
        content.put("summary_available", false);
        content.put("cross_file_search_available", false);
        return content;
    }

    private Map<String, Object> auditSummary(String logicalKbId) {
        List<ContentAuditResultRecord> latest = audits.findLatestByLogicalKbId(logicalKbId);
        Map<String, Object> summary = new LinkedHashMap<>();
        if (latest.isEmpty()) {
            summary.put("last_audited_at", null);
            summary.put("total", 0);
            summary.put("chat_eligible", 0);
            summary.put("excluded", 0);
            return summary;
        }
        int total = 0;
        int eligible = 0;
        int excluded = 0;
        String last = null;
        for (ContentAuditResultRecord row : latest) {
            total += row.totalCount();
            eligible += row.chatEligibleCount();
            excluded += row.excludedCount();
            if (row.auditedAt() != null
                    && (last == null || row.auditedAt().toString().compareTo(last) > 0)) {
                last = row.auditedAt().toString();
            }
        }
        summary.put("last_audited_at", last);
        summary.put("total", total);
        summary.put("chat_eligible", eligible);
        summary.put("excluded", excluded);
        return summary;
    }

    private Map<String, Object> scaleByProvider(String logicalKbId) {
        Map<String, Object> scale = new LinkedHashMap<>();
        for (BindingRecord binding : bindings.findByLogicalKbId(logicalKbId)) {
            scale.put(binding.providerProfile(), scaleForBinding(binding));
        }
        return scale;
    }

    private Map<String, Object> scaleForBinding(BindingRecord binding) {
        Map<String, Object> scale = new LinkedHashMap<>();
        if ("git_markdown".equals(binding.providerProfile())) {
            int files =
                    (int)
                            gitBrowse.tree(browseRequest(binding)).entries().stream()
                                    .filter(entry -> "file".equals(entry.type()))
                                    .count();
            scale.put("paths", files);
            return scale;
        }
        audits.findLatestForBinding(binding.logicalKbId(), binding.bindingId())
                .ifPresent(audit -> scale.put("documents", audit.totalCount()));
        return scale;
    }

    private List<String> sourceBadges(String logicalKbId) {
        return bindings.findByLogicalKbId(logicalKbId).stream()
                .map(BindingRecord::providerProfile)
                .distinct()
                .toList();
    }

    private static Map<String, Object> accessObject(boolean authorized) {
        Map<String, Object> access = new LinkedHashMap<>();
        access.put("authorized", authorized);
        return access;
    }

    private static String freshnessStatus() {
        return "current";
    }

    private static GitBrowse.Request browseRequest(BindingRecord binding) {
        return new GitBrowse.Request(binding.bindingId(), binding.sourceIdentityJson());
    }

    private String ownerLabel(LogicalKnowledgeBaseRecord kb) {
        if (kb.ownerUserId() == null) {
            return null;
        }
        return users
                .findById(kb.ownerUserId())
                .map(owner -> owner.displayName() == null || owner.displayName().isBlank()
                        ? owner.userId()
                        : owner.displayName())
                .orElse(kb.ownerUserId());
    }

    public record CatalogQuery(
            String q,
            String provider,
            String capability,
            String lifecycle,
            String health,
            String owner,
            String freshness,
            String cursor,
            Integer limit) {}
}
