CREATE TABLE binding_config_history (
    history_id VARCHAR2(64) NOT NULL,
    binding_id VARCHAR2(64) NOT NULL,
    logical_kb_id VARCHAR2(64) NOT NULL,
    provider_profile VARCHAR2(32) NOT NULL,
    source_identity CLOB NOT NULL,
    binding_role VARCHAR2(32) NOT NULL,
    auth_method VARCHAR2(32) NOT NULL,
    health VARCHAR2(32) NOT NULL,
    enabled NUMBER(1) NOT NULL,
    kill_switch NUMBER(1) NOT NULL,
    feature_flag NUMBER(1) NOT NULL,
    freshness_policy CLOB,
    locator_rules CLOB NOT NULL,
    credential_owner VARCHAR2(255) NOT NULL,
    region_constraints CLOB,
    config_version NUMBER(10) NOT NULL,
    captured_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_binding_config_history PRIMARY KEY (history_id),
    CONSTRAINT chk_binding_history_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_binding_history_kill_switch CHECK (kill_switch IN (0, 1)),
    CONSTRAINT chk_binding_history_feature_flag CHECK (feature_flag IN (0, 1))
);

CREATE INDEX idx_binding_history_lookup
    ON binding_config_history (binding_id, config_version);
