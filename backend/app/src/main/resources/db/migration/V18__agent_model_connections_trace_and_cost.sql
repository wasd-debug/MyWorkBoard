CREATE TABLE ai_model_connection (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    encrypted_api_key MEDIUMTEXT NULL,
    api_key_fingerprint VARCHAR(64) NULL,
    api_key_last_four VARCHAR(8) NULL,
    encryption_key_version VARCHAR(32) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    connection_status VARCHAR(32) NOT NULL DEFAULT 'UNTESTED',
    last_tested_at TIMESTAMP(6) NULL,
    last_test_error_code VARCHAR(64) NULL,
    timeout_ms INT NOT NULL DEFAULT 60000,
    max_output_tokens INT NOT NULL DEFAULT 4096,
    temperature DECIMAL(4,3) NOT NULL DEFAULT 0.100,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_ai_model_connection_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX idx_ai_model_connection_user (user_id, deleted_at, is_default, updated_at)
);

CREATE TABLE ai_model_pricing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    version INT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    input_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    output_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    cache_hit_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    cache_miss_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    reasoning_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_model_pricing_connection FOREIGN KEY (connection_id) REFERENCES ai_model_connection(id),
    CONSTRAINT fk_ai_model_pricing_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_ai_model_pricing_version (connection_id, version),
    INDEX idx_ai_model_pricing_active (connection_id, active, version)
);

ALTER TABLE agent_session
    ADD COLUMN model_connection_id VARCHAR(64) NULL AFTER queue_revision,
    ADD CONSTRAINT fk_agent_session_model_connection FOREIGN KEY (model_connection_id) REFERENCES ai_model_connection(id) ON DELETE SET NULL;

ALTER TABLE agent_turn
    ADD COLUMN model_connection_id VARCHAR(64) NULL AFTER retry_of_turn_id,
    ADD COLUMN provider_type VARCHAR(32) NULL AFTER model_connection_id,
    ADD COLUMN provider_base_host VARCHAR(255) NULL AFTER provider_type,
    ADD COLUMN model_name VARCHAR(120) NULL AFTER provider_base_host,
    ADD COLUMN model_config_revision BIGINT NULL AFTER model_name,
    ADD CONSTRAINT fk_agent_turn_model_connection FOREIGN KEY (model_connection_id) REFERENCES ai_model_connection(id) ON DELETE SET NULL;

CREATE TABLE ai_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    model_connection_id VARCHAR(64) NULL,
    provider_type VARCHAR(32) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    round_no INT NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    cache_hit_tokens BIGINT NOT NULL DEFAULT 0,
    cache_miss_tokens BIGINT NOT NULL DEFAULT 0,
    reasoning_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    first_token_ms BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    pricing_version INT NULL,
    currency VARCHAR(8) NULL,
    input_unit_price DECIMAL(20,8) NULL,
    output_unit_price DECIMAL(20,8) NULL,
    cache_hit_unit_price DECIMAL(20,8) NULL,
    cache_miss_unit_price DECIMAL(20,8) NULL,
    reasoning_unit_price DECIMAL(20,8) NULL,
    estimated_input_cost DECIMAL(24,12) NULL,
    estimated_output_cost DECIMAL(24,12) NULL,
    estimated_cache_cost DECIMAL(24,12) NULL,
    estimated_reasoning_cost DECIMAL(24,12) NULL,
    estimated_total_cost DECIMAL(24,12) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_usage_turn FOREIGN KEY (turn_id) REFERENCES agent_turn(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_usage_session FOREIGN KEY (session_id) REFERENCES agent_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_ai_usage_connection FOREIGN KEY (model_connection_id) REFERENCES ai_model_connection(id) ON DELETE SET NULL,
    UNIQUE KEY uk_ai_usage_turn_round (turn_id, round_no),
    INDEX idx_ai_usage_user_created (user_id, created_at),
    INDEX idx_ai_usage_model_created (model_connection_id, created_at)
);

CREATE TABLE agent_tool_call (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    tool_name VARCHAR(160) NOT NULL,
    tool_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    status VARCHAR(32) NOT NULL,
    arguments_summary VARCHAR(1000) NULL,
    result_summary VARCHAR(1000) NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_tool_call_turn FOREIGN KEY (turn_id) REFERENCES agent_turn(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tool_call_session FOREIGN KEY (session_id) REFERENCES agent_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tool_call_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_agent_tool_call_turn_sequence (turn_id, sequence_no),
    INDEX idx_agent_tool_call_user_created (user_id, created_at)
);

CREATE TABLE agent_prompt_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt_type VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_agent_prompt_version (prompt_type, version)
);

INSERT INTO agent_prompt_version(prompt_type, version, content_hash, active)
VALUES ('SYSTEM', 'v1', SHA2('agent-system-v1', 256), TRUE);
