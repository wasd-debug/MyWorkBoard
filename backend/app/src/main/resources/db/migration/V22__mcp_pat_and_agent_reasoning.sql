CREATE TABLE mcp_personal_token (
    id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_hint VARCHAR(24) NOT NULL,
    scopes JSON NOT NULL,
    book_ids JSON NULL,
    expires_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    last_used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_personal_token_hash (token_hash),
    KEY idx_mcp_personal_token_user (user_id, created_at),
    CONSTRAINT fk_mcp_personal_token_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mcp_tool_call (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    tool_name VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    parameter_summary JSON NULL,
    result_summary VARCHAR(500) NULL,
    error_code VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mcp_tool_call_token_created (token_id, created_at),
    KEY idx_mcp_tool_call_user_created (user_id, created_at),
    CONSTRAINT fk_mcp_tool_call_token FOREIGN KEY (token_id) REFERENCES mcp_personal_token(id),
    CONSTRAINT fk_mcp_tool_call_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE agent_turn
    ADD COLUMN deep_thinking BOOLEAN NOT NULL DEFAULT FALSE AFTER user_message,
    ADD COLUMN reasoning_content MEDIUMTEXT NULL AFTER assistant_content;
