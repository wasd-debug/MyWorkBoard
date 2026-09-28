CREATE TABLE mcp_oauth_client (
    client_id VARCHAR(191) NOT NULL,
    client_name VARCHAR(160) NOT NULL,
    redirect_uris JSON NOT NULL,
    grant_types JSON NOT NULL,
    response_types JSON NOT NULL,
    token_endpoint_auth_method VARCHAR(32) NOT NULL DEFAULT 'none',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (client_id),
    KEY idx_mcp_oauth_client_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mcp_grant (
    id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    client_id VARCHAR(191) NOT NULL,
    scopes JSON NOT NULL,
    book_ids JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_grant_user_client (user_id, client_id),
    KEY idx_mcp_grant_user_updated (user_id, updated_at),
    CONSTRAINT fk_mcp_grant_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_mcp_grant_client FOREIGN KEY (client_id) REFERENCES mcp_oauth_client(client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE mcp_personal_token
    ADD COLUMN token_type VARCHAR(16) NOT NULL DEFAULT 'PAT' AFTER name,
    ADD COLUMN oauth_client_id VARCHAR(191) NULL AFTER token_type,
    ADD COLUMN oauth_grant_id CHAR(36) NULL AFTER oauth_client_id,
    ADD COLUMN refresh_token_hash CHAR(64) NULL AFTER token_hash,
    ADD COLUMN refresh_expires_at TIMESTAMP(6) NULL AFTER expires_at,
    ADD UNIQUE KEY uk_mcp_personal_refresh_hash (refresh_token_hash),
    ADD KEY idx_mcp_personal_oauth_grant (oauth_grant_id, revoked_at),
    ADD CONSTRAINT fk_mcp_personal_oauth_client FOREIGN KEY (oauth_client_id) REFERENCES mcp_oauth_client(client_id),
    ADD CONSTRAINT fk_mcp_personal_oauth_grant FOREIGN KEY (oauth_grant_id) REFERENCES mcp_grant(id);

CREATE TABLE mcp_oauth_code (
    id CHAR(36) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    grant_id CHAR(36) NOT NULL,
    client_id VARCHAR(191) NOT NULL,
    user_id BIGINT NOT NULL,
    redirect_uri VARCHAR(1000) NOT NULL,
    resource_uri VARCHAR(1000) NOT NULL,
    scopes JSON NOT NULL,
    book_ids JSON NULL,
    code_challenge VARCHAR(160) NOT NULL,
    code_challenge_method VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_oauth_code_hash (code_hash),
    KEY idx_mcp_oauth_code_expiry (expires_at, consumed_at),
    CONSTRAINT fk_mcp_oauth_code_grant FOREIGN KEY (grant_id) REFERENCES mcp_grant(id),
    CONSTRAINT fk_mcp_oauth_code_client FOREIGN KEY (client_id) REFERENCES mcp_oauth_client(client_id),
    CONSTRAINT fk_mcp_oauth_code_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
