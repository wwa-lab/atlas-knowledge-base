package com.atlas.knowledgebase.registry;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LogicalKnowledgeBaseRepository {

    static final String RESOURCE_TYPE = "logical_knowledge_base";

    private static final RowMapper<LogicalKnowledgeBaseRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new LogicalKnowledgeBaseRecord(
                            rs.getString("logical_kb_id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("owner_user_id"),
                            rs.getString("discoverability"),
                            rs.getString("purpose"),
                            rs.getString("classification"),
                            rs.getInt("model_eligible") == 1,
                            rs.getString("capability"),
                            rs.getString("lifecycle"),
                            rs.getString("health"),
                            rs.getInt("config_version"),
                            rs.getString("max_staleness"),
                            rs.getInt("freshness_required") == 1,
                            rs.getString("access_request_url"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            optionalInstant(rs.getTimestamp("activated_at")));

    private final JdbcTemplate jdbcTemplate;

    public LogicalKnowledgeBaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public LogicalKnowledgeBaseRecord insert(LogicalKnowledgeBaseRecord kb) {
        Instant created = kb.createdAt() != null ? kb.createdAt() : Instant.now();
        Instant updated = kb.updatedAt() != null ? kb.updatedAt() : created;
        int version = kb.configVersion() > 0 ? kb.configVersion() : 1;
        String lifecycle = kb.lifecycle() != null ? kb.lifecycle() : "draft";
        jdbcTemplate.update(
                """
                INSERT INTO logical_knowledge_base (
                  logical_kb_id, name, description, owner_user_id, discoverability, purpose,
                  classification, model_eligible, capability, lifecycle, health, config_version,
                  max_staleness, freshness_required, access_request_url, created_at, updated_at,
                  activated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                kb.logicalKbId(),
                kb.name(),
                kb.description(),
                kb.ownerUserId(),
                kb.discoverability(),
                kb.purpose(),
                kb.classification(),
                kb.modelEligible() ? 1 : 0,
                kb.capability(),
                lifecycle,
                kb.health(),
                version,
                kb.maxStaleness(),
                kb.freshnessRequired() ? 1 : 0,
                kb.accessRequestUrl(),
                Timestamp.from(created),
                Timestamp.from(updated),
                kb.activatedAt() == null ? null : Timestamp.from(kb.activatedAt()));
        return findById(kb.logicalKbId()).orElseThrow();
    }

    public Optional<LogicalKnowledgeBaseRecord> findById(String logicalKbId) {
        return jdbcTemplate
                .query(
                        "SELECT * FROM logical_knowledge_base WHERE logical_kb_id = ?",
                        ROW_MAPPER,
                        logicalKbId)
                .stream()
                .findFirst();
    }

    /**
     * Updates draft configuration when {@code expectedVersion} matches. Bumps {@code
     * config_version} by one.
     */
    @Transactional
    public LogicalKnowledgeBaseRecord updateDraft(
            String logicalKbId, int expectedVersion, LogicalKnowledgeBaseDraft draft) {
        Instant now = Instant.now();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE logical_knowledge_base
                        SET name = ?, description = ?, owner_user_id = ?, discoverability = ?,
                            purpose = ?, classification = ?, model_eligible = ?, capability = ?,
                            health = ?, max_staleness = ?, freshness_required = ?,
                            access_request_url = ?, config_version = config_version + 1,
                            updated_at = ?
                        WHERE logical_kb_id = ? AND config_version = ? AND lifecycle = 'draft'
                        """,
                        draft.name(),
                        draft.description(),
                        draft.ownerUserId(),
                        draft.discoverability(),
                        draft.purpose(),
                        draft.classification(),
                        draft.modelEligible() ? 1 : 0,
                        draft.capability(),
                        draft.health(),
                        draft.maxStaleness(),
                        draft.freshnessRequired() ? 1 : 0,
                        draft.accessRequestUrl(),
                        Timestamp.from(now),
                        logicalKbId,
                        expectedVersion);
        if (updated == 1) {
            return findById(logicalKbId).orElseThrow();
        }
        throw conflictOrState(logicalKbId, expectedVersion, "draft");
    }

    /**
     * Activates a draft when {@code expectedVersion} matches. Bumps {@code config_version} by one.
     */
    @Transactional
    public LogicalKnowledgeBaseRecord activate(String logicalKbId, int expectedVersion) {
        Instant now = Instant.now();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE logical_knowledge_base
                        SET lifecycle = 'active',
                            config_version = config_version + 1,
                            activated_at = ?,
                            updated_at = ?
                        WHERE logical_kb_id = ? AND config_version = ? AND lifecycle = 'draft'
                        """,
                        Timestamp.from(now),
                        Timestamp.from(now),
                        logicalKbId,
                        expectedVersion);
        if (updated == 1) {
            return findById(logicalKbId).orElseThrow();
        }
        throw conflictOrState(logicalKbId, expectedVersion, "draft");
    }

    /**
     * Activates a draft, optionally forcing capability (Git without {@code .kb} → browse_only).
     */
    @Transactional
    public LogicalKnowledgeBaseRecord activate(
            String logicalKbId, int expectedVersion, String capability, boolean modelEligible) {
        Instant now = Instant.now();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE logical_knowledge_base
                        SET lifecycle = 'active',
                            capability = ?,
                            model_eligible = ?,
                            config_version = config_version + 1,
                            activated_at = ?,
                            updated_at = ?
                        WHERE logical_kb_id = ? AND config_version = ? AND lifecycle = 'draft'
                        """,
                        capability,
                        modelEligible ? 1 : 0,
                        Timestamp.from(now),
                        Timestamp.from(now),
                        logicalKbId,
                        expectedVersion);
        if (updated == 1) {
            return findById(logicalKbId).orElseThrow();
        }
        throw conflictOrState(logicalKbId, expectedVersion, "draft");
    }

    @Transactional
    public LogicalKnowledgeBaseRecord suspend(String logicalKbId) {
        Instant now = Instant.now();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE logical_knowledge_base
                        SET lifecycle = 'suspended', updated_at = ?
                        WHERE logical_kb_id = ? AND lifecycle IN ('draft', 'active')
                        """,
                        Timestamp.from(now),
                        logicalKbId);
        if (updated == 1) {
            return findById(logicalKbId).orElseThrow();
        }
        LogicalKnowledgeBaseRecord current =
                findById(logicalKbId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "logical knowledge base not found: " + logicalKbId));
        if ("suspended".equals(current.lifecycle())) {
            return current;
        }
        throw new IllegalStateException(
                "logical knowledge base " + logicalKbId + " is " + current.lifecycle());
    }

    private RuntimeException conflictOrState(
            String logicalKbId, int expectedVersion, String requiredLifecycle) {
        LogicalKnowledgeBaseRecord current =
                findById(logicalKbId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "logical knowledge base not found: " + logicalKbId));
        if (current.configVersion() != expectedVersion) {
            return new ConfigVersionConflictException(
                    RESOURCE_TYPE, logicalKbId, expectedVersion, current.configVersion());
        }
        return new IllegalStateException(
                "logical knowledge base "
                        + logicalKbId
                        + " is "
                        + current.lifecycle()
                        + ", expected "
                        + requiredLifecycle);
    }

    private static Instant optionalInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
