ALTER TABLE mcp_oauth_client
    ADD COLUMN registration_ip VARCHAR(64) NULL AFTER token_endpoint_auth_method,
    ADD COLUMN registration_user_agent VARCHAR(255) NULL AFTER registration_ip,
    ADD COLUMN last_used_at TIMESTAMP(6) NULL AFTER registration_user_agent,
    ADD COLUMN last_ip VARCHAR(64) NULL AFTER last_used_at,
    ADD COLUMN last_user_agent VARCHAR(255) NULL AFTER last_ip,
    ADD KEY idx_mcp_oauth_client_registration (registration_ip, created_at);

ALTER TABLE mcp_grant
    ADD COLUMN last_used_at TIMESTAMP(6) NULL AFTER updated_at;

ALTER TABLE mcp_tool_call
    ADD COLUMN auth_type VARCHAR(16) NOT NULL DEFAULT 'PAT' AFTER user_id,
    ADD COLUMN oauth_client_id VARCHAR(191) NULL AFTER auth_type,
    ADD KEY idx_mcp_tool_call_client_created (oauth_client_id, created_at);

CREATE TABLE mcp_protocol_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    client_id VARCHAR(191) NULL,
    token_id CHAR(36) NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    detail_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_mcp_protocol_event_user_created (user_id, created_at),
    KEY idx_mcp_protocol_event_client_created (client_id, created_at),
    KEY idx_mcp_protocol_event_type_created (event_type, created_at),
    CONSTRAINT fk_mcp_protocol_event_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_mcp_protocol_event_client FOREIGN KEY (client_id) REFERENCES mcp_oauth_client(client_id),
    CONSTRAINT fk_mcp_protocol_event_token FOREIGN KEY (token_id) REFERENCES mcp_personal_token(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
