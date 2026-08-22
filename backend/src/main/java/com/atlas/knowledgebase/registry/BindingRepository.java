package com.atlas.knowledgebase.registry;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BindingRepository {

    static final String RESOURCE_TYPE = "binding";

    private static final RowMapper<BindingRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new BindingRecord(
                            rs.getString("binding_id"),
                            rs.getString("logical_kb_id"),
                            rs.getString("provider_profile"),
                            rs.getString("source_identity"),
                            rs.getString("binding_role"),
                            rs.getString("auth_method"),
                            rs.getString("health"),
                            rs.getInt("enabled") == 1,
                            rs.getInt("kill_switch") == 1,
                            rs.getInt("feature_flag") == 1,
                            rs.getString("freshness_policy"),
                            rs.getString("locator_rules"),
                            rs.getString("credential_owner"),
                            rs.getString("region_constraints"),
                            rs.getInt("config_version"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<BindingConfigHistoryRecord> HISTORY_ROW_MAPPER =
            (rs, rowNum) ->
                    new BindingConfigHistoryRecord(
                            rs.getString("history_id"),
                            rs.getString("binding_id"),
                            rs.getString("logical_kb_id"),
                            rs.getString("provider_profile"),
                            rs.getString("source_identity"),
                            rs.getString("binding_role"),
                            rs.getString("auth_method"),
                            rs.getString("health"),
                            rs.getInt("enabled") == 1,
                            rs.getInt("kill_switch") == 1,
                            rs.getInt("feature_flag") == 1,
                            rs.getString("freshness_policy"),
                            rs.getString("locator_rules"),
                            rs.getString("credential_owner"),
                            rs.getString("region_constraints"),
                            rs.getInt("config_version"),
                            rs.getTimestamp("captured_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public BindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BindingRecord insert(BindingRecord binding) {
        Instant created = binding.createdAt() != null ? binding.createdAt() : Instant.now();
        Instant updated = binding.updatedAt() != null ? binding.updatedAt() : created;
        int version = binding.configVersion() > 0 ? binding.configVersion() : 1;
        jdbcTemplate.update(
                """
                INSERT INTO binding (
                  binding_id, logical_kb_id, provider_profile, source_identity, binding_role,
                  auth_method, health, enabled, kill_switch, feature_flag, freshness_policy,
                  locator_rules, credential_owner, region_constraints, config_version,
                  created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                binding.bindingId(),
                binding.logicalKbId(),
                binding.providerProfile(),
                binding.sourceIdentityJson(),
                binding.bindingRole(),
                binding.authMethod(),
                binding.health(),
                binding.enabled() ? 1 : 0,
                binding.killSwitch() ? 1 : 0,
                binding.featureFlag() ? 1 : 0,
                binding.freshnessPolicyJson(),
                binding.locatorRulesJson(),
                binding.credentialOwner(),
                binding.regionConstraintsJson(),
                version,
                Timestamp.from(created),
                Timestamp.from(updated));
        return findById(binding.bindingId()).orElseThrow();
    }

    public Optional<BindingRecord> findById(String bindingId) {
        return jdbcTemplate
                .query("SELECT * FROM binding WHERE binding_id = ?", ROW_MAPPER, bindingId)
                .stream()
                .findFirst();
    }

    public List<BindingRecord> findByLogicalKbId(String logicalKbId) {
        return jdbcTemplate.query(
                "SELECT * FROM binding WHERE logical_kb_id = ?", ROW_MAPPER, logicalKbId);
    }

    /** Captures the complete prior state for an immutable, auditable rollback target. */
    @Transactional
    public BindingConfigHistoryRecord snapshot(BindingRecord binding) {
        BindingConfigHistoryRecord history =
                BindingConfigHistoryRecord.from(
                        binding, "bch_" + UUID.randomUUID().toString().replace("-", ""), Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO binding_config_history (
                  history_id, binding_id, logical_kb_id, provider_profile, source_identity,
                  binding_role, auth_method, health, enabled, kill_switch, feature_flag,
                  freshness_policy, locator_rules, credential_owner, region_constraints,
                  config_version, captured_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                history.historyId(),
                history.bindingId(),
                history.logicalKbId(),
                history.providerProfile(),
                history.sourceIdentityJson(),
                history.bindingRole(),
                history.authMethod(),
                history.health(),
                history.enabled() ? 1 : 0,
                history.killSwitch() ? 1 : 0,
                history.featureFlag() ? 1 : 0,
                history.freshnessPolicyJson(),
                history.locatorRulesJson(),
                history.credentialOwner(),
                history.regionConstraintsJson(),
                history.configVersion(),
                Timestamp.from(history.capturedAt()));
        return history;
    }

    public Optional<BindingConfigHistoryRecord> findHistory(String bindingId, int configVersion) {
        return jdbcTemplate
                .query(
                        """
                        SELECT * FROM binding_config_history
                        WHERE binding_id = ? AND config_version = ?
                        ORDER BY captured_at DESC
                        """,
                        HISTORY_ROW_MAPPER,
                        bindingId,
                        configVersion)
                .stream()
                .findFirst();
    }

    public Optional<BindingConfigHistoryRecord> findLatestHistory(String bindingId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT * FROM binding_config_history
                        WHERE binding_id = ?
                        ORDER BY config_version DESC, captured_at DESC
                        """,
                        HISTORY_ROW_MAPPER,
                        bindingId)
                .stream()
                .findFirst();
    }

    @Transactional
    public int deleteByLogicalKbId(String logicalKbId) {
        return jdbcTemplate.update("DELETE FROM binding WHERE logical_kb_id = ?", logicalKbId);
    }

    @Transactional
    public BindingRecord update(String bindingId, int expectedVersion, BindingDraft draft) {
        Instant now = Instant.now();
        BindingRecord current =
                findById(bindingId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("binding not found: " + bindingId));
        if (current.configVersion() != expectedVersion) {
            throw new ConfigVersionConflictException(
                    RESOURCE_TYPE, bindingId, expectedVersion, current.configVersion());
        }
        snapshot(current);
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE binding
                        SET provider_profile = ?, source_identity = ?, binding_role = ?,
                            auth_method = ?, health = ?, enabled = ?, kill_switch = ?,
                            feature_flag = ?, freshness_policy = ?, locator_rules = ?,
                            credential_owner = ?, region_constraints = ?,
                            config_version = config_version + 1, updated_at = ?
                        WHERE binding_id = ? AND config_version = ?
                        """,
                        draft.providerProfile(),
                        draft.sourceIdentityJson(),
                        draft.bindingRole(),
                        draft.authMethod(),
                        draft.health(),
                        draft.enabled() ? 1 : 0,
                        draft.killSwitch() ? 1 : 0,
                        draft.featureFlag() ? 1 : 0,
                        draft.freshnessPolicyJson(),
                        draft.locatorRulesJson(),
                        draft.credentialOwner(),
                        draft.regionConstraintsJson(),
                        Timestamp.from(now),
                        bindingId,
                        expectedVersion);
        if (updated == 1) {
            return findById(bindingId).orElseThrow();
        }
        current =
                findById(bindingId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("binding not found: " + bindingId));
        throw new ConfigVersionConflictException(
                RESOURCE_TYPE, bindingId, expectedVersion, current.configVersion());
    }

    /** Atomically changes runtime controls while preserving every other binding field. */
    @Transactional
    public BindingRecord updateRuntime(
            String bindingId, int expectedVersion, boolean enabled, boolean killSwitch) {
        BindingRecord current =
                findById(bindingId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("binding not found: " + bindingId));
        if (current.configVersion() != expectedVersion) {
            throw new ConfigVersionConflictException(
                    RESOURCE_TYPE, bindingId, expectedVersion, current.configVersion());
        }
        if (current.enabled() == enabled && current.killSwitch() == killSwitch) {
            return current;
        }
        snapshot(current);
        Instant now = Instant.now();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE binding
                        SET enabled = ?, kill_switch = ?, config_version = config_version + 1,
                            updated_at = ?
                        WHERE binding_id = ? AND config_version = ?
                        """,
                        enabled ? 1 : 0,
                        killSwitch ? 1 : 0,
                        Timestamp.from(now),
                        bindingId,
                        expectedVersion);
        if (updated == 1) {
            return findById(bindingId).orElseThrow();
        }
        BindingRecord actual = findById(bindingId).orElseThrow();
        throw new ConfigVersionConflictException(
                RESOURCE_TYPE, bindingId, expectedVersion, actual.configVersion());
    }

    /** Restores an immutable historical snapshot, keeping the live version monotonic. */
    @Transactional
    public BindingRecord restore(
            String bindingId, int expectedVersion, BindingConfigHistoryRecord target) {
        BindingRecord current =
                findById(bindingId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("binding not found: " + bindingId));
        if (current.configVersion() != expectedVersion) {
            throw new ConfigVersionConflictException(
                    RESOURCE_TYPE, bindingId, expectedVersion, current.configVersion());
        }
        if (!bindingId.equals(target.bindingId())) {
            throw new IllegalArgumentException("history target belongs to another binding");
        }
        snapshot(current);
        Instant now = Instant.now();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE binding
                        SET provider_profile = ?, source_identity = ?, binding_role = ?,
                            auth_method = ?, health = ?, enabled = ?, kill_switch = ?,
                            feature_flag = ?, freshness_policy = ?, locator_rules = ?,
                            credential_owner = ?, region_constraints = ?,
                            config_version = config_version + 1, updated_at = ?
                        WHERE binding_id = ? AND config_version = ?
                        """,
                        target.providerProfile(),
                        target.sourceIdentityJson(),
                        target.bindingRole(),
                        target.authMethod(),
                        target.health(),
                        target.enabled() ? 1 : 0,
                        target.killSwitch() ? 1 : 0,
                        target.featureFlag() ? 1 : 0,
                        target.freshnessPolicyJson(),
                        target.locatorRulesJson(),
                        target.credentialOwner(),
                        target.regionConstraintsJson(),
                        Timestamp.from(now),
                        bindingId,
                        expectedVersion);
        if (updated == 1) {
            return findById(bindingId).orElseThrow();
        }
        BindingRecord actual = findById(bindingId).orElseThrow();
        throw new ConfigVersionConflictException(
                RESOURCE_TYPE, bindingId, expectedVersion, actual.configVersion());
    }
}
