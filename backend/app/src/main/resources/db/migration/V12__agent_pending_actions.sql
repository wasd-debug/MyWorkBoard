CREATE TABLE agent_pending_action (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_version INT NOT NULL,
    input_json JSON NOT NULL,
    expected_revision BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_pending_action_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX idx_agent_pending_action_user_status (user_id, status, expires_at),
    INDEX idx_agent_pending_action_expires (expires_at)
);
