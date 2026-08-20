-- Core MVP entities from mvp-data-model.md (TASK-006).
-- Portable across H2 Oracle MODE (local) and Oracle 19c (non-prod/prod).
-- JSON / string-array logical types are VARCHAR2 or CLOB, not a vendor JSON type.
-- Booleans are NUMBER(1) because Oracle 19c has no BOOLEAN.
-- Do not store GitHub/Confluence document bodies as Atlas source of truth.

CREATE TABLE atlas_user (
    user_id VARCHAR2(64) NOT NULL,
    sso_subject VARCHAR2(256) NOT NULL,
    display_name VARCHAR2(255),
    email VARCHAR2(320),
    roles VARCHAR2(4000) DEFAULT '[]' NOT NULL,
    model_entitled NUMBER(1) DEFAULT 0 NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_atlas_user PRIMARY KEY (user_id),
    CONSTRAINT uq_atlas_user_sso_subject UNIQUE (sso_subject),
    CONSTRAINT chk_atlas_user_entitled CHECK (model_entitled IN (0, 1))
);

CREATE TABLE atlas_session (
    session_id VARCHAR2(64) NOT NULL,
    user_id VARCHAR2(64) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    absolute_expires_at TIMESTAMP NOT NULL,
    idle_expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    csrf_secret VARCHAR2(512) NOT NULL,
    CONSTRAINT pk_atlas_session PRIMARY KEY (session_id),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES atlas_user (user_id)
);

CREATE INDEX idx_atlas_session_user_id ON atlas_session (user_id);

CREATE TABLE provider_connection (
    connection_id VARCHAR2(64) NOT NULL,
    user_id VARCHAR2(64) NOT NULL,
    provider VARCHAR2(32) NOT NULL,
    status VARCHAR2(32) NOT NULL,
    granted_scopes VARCHAR2(4000) DEFAULT '[]' NOT NULL,
    expires_at TIMESTAMP,
    last_verified_at TIMESTAMP,
    secret_ref VARCHAR2(512) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_provider_connection PRIMARY KEY (connection_id),
    CONSTRAINT uq_provider_connection_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_provider_connection_user FOREIGN KEY (user_id) REFERENCES atlas_user (user_id),
    CONSTRAINT chk_provider_connection_provider CHECK (provider IN ('github', 'confluence')),
    CONSTRAINT chk_provider_connection_status CHECK (status IN (
        'connected', 'expired', 'reconnect_required', 'revoked'
    ))
);

CREATE TABLE logical_knowledge_base (
    logical_kb_id VARCHAR2(64) NOT NULL,
    name VARCHAR2(255) NOT NULL,
    description VARCHAR2(4000),
    owner_user_id VARCHAR2(64),
    discoverability VARCHAR2(32) NOT NULL,
    purpose VARCHAR2(1000) NOT NULL,
    classification VARCHAR2(128) NOT NULL,
    model_eligible NUMBER(1) DEFAULT 0 NOT NULL,
    capability VARCHAR2(32) NOT NULL,
    lifecycle VARCHAR2(32) NOT NULL,
    health VARCHAR2(32) NOT NULL,
    config_version NUMBER(10) DEFAULT 1 NOT NULL,
    max_staleness VARCHAR2(64),
    freshness_required NUMBER(1) DEFAULT 0 NOT NULL,
    access_request_url VARCHAR2(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    activated_at TIMESTAMP,
    CONSTRAINT pk_logical_knowledge_base PRIMARY KEY (logical_kb_id),
    CONSTRAINT fk_lkb_owner FOREIGN KEY (owner_user_id) REFERENCES atlas_user (user_id),
    CONSTRAINT chk_lkb_discoverability CHECK (discoverability IN ('catalog', 'private')),
    CONSTRAINT chk_lkb_capability CHECK (capability IN ('chat_ready', 'browse_only')),
    CONSTRAINT chk_lkb_lifecycle CHECK (lifecycle IN ('draft', 'active', 'suspended', 'retired')),
    CONSTRAINT chk_lkb_health CHECK (health IN ('healthy', 'degraded', 'unavailable')),
    CONSTRAINT chk_lkb_model_eligible CHECK (model_eligible IN (0, 1)),
    CONSTRAINT chk_lkb_freshness_required CHECK (freshness_required IN (0, 1))
);

CREATE TABLE binding (
    binding_id VARCHAR2(64) NOT NULL,
    logical_kb_id VARCHAR2(64) NOT NULL,
    provider_profile VARCHAR2(32) NOT NULL,
    source_identity CLOB NOT NULL,
    binding_role VARCHAR2(32) NOT NULL,
    auth_method VARCHAR2(32) NOT NULL,
    health VARCHAR2(32) NOT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    kill_switch NUMBER(1) DEFAULT 0 NOT NULL,
    feature_flag NUMBER(1) DEFAULT 0 NOT NULL,
    freshness_policy CLOB,
    locator_rules CLOB NOT NULL,
    credential_owner VARCHAR2(255) NOT NULL,
    region_constraints CLOB,
    config_version NUMBER(10) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_binding PRIMARY KEY (binding_id),
    CONSTRAINT fk_binding_lkb FOREIGN KEY (logical_kb_id) REFERENCES logical_knowledge_base (logical_kb_id),
    CONSTRAINT chk_binding_profile CHECK (provider_profile IN ('dify', 'git_markdown', 'confluence')),
    CONSTRAINT chk_binding_role CHECK (binding_role IN ('canonical', 'mirror', 'supplemental')),
    CONSTRAINT chk_binding_auth CHECK (auth_method IN (
        'delegated_user', 'sso_group_mapping', 'other_approved'
    )),
    CONSTRAINT chk_binding_health CHECK (health IN ('healthy', 'degraded', 'unavailable')),
    CONSTRAINT chk_binding_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_binding_kill_switch CHECK (kill_switch IN (0, 1)),
    CONSTRAINT chk_binding_feature_flag CHECK (feature_flag IN (0, 1))
);

CREATE INDEX idx_binding_logical_kb_id ON binding (logical_kb_id);
CREATE INDEX idx_binding_profile_enabled ON binding (provider_profile, enabled);

CREATE TABLE content_audit_result (
    audit_id VARCHAR2(64) NOT NULL,
    logical_kb_id VARCHAR2(64) NOT NULL,
    binding_id VARCHAR2(64) NOT NULL,
    total_count NUMBER(10) NOT NULL,
    chat_eligible_count NUMBER(10) NOT NULL,
    excluded_count NUMBER(10) NOT NULL,
    exclusion_reasons CLOB NOT NULL,
    remediation_blob_ref VARCHAR2(512),
    audited_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_content_audit_result PRIMARY KEY (audit_id),
    CONSTRAINT fk_audit_result_lkb FOREIGN KEY (logical_kb_id) REFERENCES logical_knowledge_base (logical_kb_id),
    CONSTRAINT fk_audit_result_binding FOREIGN KEY (binding_id) REFERENCES binding (binding_id)
);

CREATE INDEX idx_content_audit_logical_kb ON content_audit_result (logical_kb_id);

CREATE TABLE chat_thread (
    thread_id VARCHAR2(64) NOT NULL,
    user_id VARCHAR2(64) NOT NULL,
    title VARCHAR2(255),
    selected_logical_kb_ids VARCHAR2(4000) DEFAULT '[]' NOT NULL,
    branched_from_thread_id VARCHAR2(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT pk_chat_thread PRIMARY KEY (thread_id),
    CONSTRAINT fk_thread_user FOREIGN KEY (user_id) REFERENCES atlas_user (user_id),
    CONSTRAINT fk_thread_branch FOREIGN KEY (branched_from_thread_id) REFERENCES chat_thread (thread_id)
);

CREATE INDEX idx_chat_thread_user_updated ON chat_thread (user_id, updated_at);

CREATE TABLE chat_message (
    message_id VARCHAR2(64) NOT NULL,
    thread_id VARCHAR2(64) NOT NULL,
    message_role VARCHAR2(32) NOT NULL,
    status VARCHAR2(32) NOT NULL,
    question_text CLOB,
    answer_text CLOB,
    logical_kb_scope VARCHAR2(4000) DEFAULT '[]' NOT NULL,
    binding_set VARCHAR2(4000) DEFAULT '[]' NOT NULL,
    config_versions CLOB NOT NULL,
    coverage CLOB,
    conflict_section CLOB,
    classification VARCHAR2(128),
    request_id VARCHAR2(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT pk_chat_message PRIMARY KEY (message_id),
    CONSTRAINT fk_message_thread FOREIGN KEY (thread_id) REFERENCES chat_thread (thread_id),
    CONSTRAINT chk_message_role CHECK (message_role IN ('user', 'assistant', 'system_notice')),
    CONSTRAINT chk_message_status CHECK (status IN (
        'processing', 'streaming', 'completed', 'incomplete_cancelled', 'failed'
    ))
);

CREATE INDEX idx_chat_message_thread_id ON chat_message (thread_id);

CREATE TABLE citation (
    citation_id VARCHAR2(64) NOT NULL,
    message_id VARCHAR2(64) NOT NULL,
    logical_kb_id VARCHAR2(64) NOT NULL,
    binding_id VARCHAR2(64) NOT NULL,
    provider VARCHAR2(32) NOT NULL,
    locator CLOB NOT NULL,
    version_label VARCHAR2(128),
    excerpt CLOB,
    document_title VARCHAR2(512),
    owner VARCHAR2(255),
    classification VARCHAR2(128),
    source_updated_at TIMESTAMP,
    atlas_verified_at TIMESTAMP,
    resolve_status VARCHAR2(32),
    CONSTRAINT pk_citation PRIMARY KEY (citation_id),
    CONSTRAINT fk_citation_message FOREIGN KEY (message_id) REFERENCES chat_message (message_id),
    CONSTRAINT fk_citation_lkb FOREIGN KEY (logical_kb_id) REFERENCES logical_knowledge_base (logical_kb_id),
    CONSTRAINT fk_citation_binding FOREIGN KEY (binding_id) REFERENCES binding (binding_id),
    CONSTRAINT chk_citation_provider CHECK (provider IN ('dify', 'git_markdown', 'confluence')),
    CONSTRAINT chk_citation_resolve CHECK (
        resolve_status IS NULL OR resolve_status IN ('ok', 'moved', 'unavailable', 'unknown')
    )
);

CREATE INDEX idx_citation_message_id ON citation (message_id);

CREATE TABLE issue_report (
    issue_id VARCHAR2(64) NOT NULL,
    user_id VARCHAR2(64) NOT NULL,
    message_id VARCHAR2(64),
    citation_id VARCHAR2(64),
    category VARCHAR2(32) NOT NULL,
    diagnostics CLOB NOT NULL,
    route_target VARCHAR2(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_issue_report PRIMARY KEY (issue_id),
    CONSTRAINT fk_issue_user FOREIGN KEY (user_id) REFERENCES atlas_user (user_id),
    CONSTRAINT fk_issue_message FOREIGN KEY (message_id) REFERENCES chat_message (message_id),
    CONSTRAINT fk_issue_citation FOREIGN KEY (citation_id) REFERENCES citation (citation_id),
    CONSTRAINT chk_issue_category CHECK (category IN (
        'content', 'citation', 'retrieval', 'permission_connection', 'model', 'system_security'
    ))
);

CREATE TABLE audit_event (
    event_id VARCHAR2(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    user_id VARCHAR2(64),
    logical_kb_id VARCHAR2(64),
    binding_id VARCHAR2(64),
    connector VARCHAR2(64),
    action VARCHAR2(64) NOT NULL,
    authorization_result VARCHAR2(64),
    evidence_locator_ids VARCHAR2(4000),
    model_id VARCHAR2(128),
    latency_ms NUMBER(10),
    status VARCHAR2(32) NOT NULL,
    error_category VARCHAR2(64),
    details CLOB,
    CONSTRAINT pk_audit_event PRIMARY KEY (event_id)
);

CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at);
