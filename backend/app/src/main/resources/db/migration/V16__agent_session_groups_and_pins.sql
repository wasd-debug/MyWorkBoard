CREATE TABLE agent_session_group (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_session_group_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_agent_session_group_user_name (user_id, name),
    INDEX idx_agent_session_group_user_sort (user_id, sort_order, created_at)
);

ALTER TABLE agent_session
    ADD COLUMN group_id VARCHAR(64) NULL AFTER user_id,
    ADD COLUMN pinned_at TIMESTAMP(6) NULL AFTER archived_at,
    ADD CONSTRAINT fk_agent_session_group FOREIGN KEY (group_id) REFERENCES agent_session_group(id) ON DELETE SET NULL,
    ADD INDEX idx_agent_session_user_group (user_id, group_id, updated_at),
    ADD INDEX idx_agent_session_user_pinned (user_id, pinned_at, updated_at);
