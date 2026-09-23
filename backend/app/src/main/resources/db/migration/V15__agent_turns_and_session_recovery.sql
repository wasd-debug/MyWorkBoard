ALTER TABLE agent_session
    ADD COLUMN archived_at TIMESTAMP(6) NULL AFTER updated_at;

ALTER TABLE agent_message
    ADD COLUMN turn_id VARCHAR(64) NULL AFTER user_id,
    ADD COLUMN metadata_json JSON NULL AFTER content,
    ADD INDEX idx_agent_message_turn (turn_id);

CREATE TABLE agent_turn (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    user_message MEDIUMTEXT NOT NULL,
    assistant_content MEDIUMTEXT NULL,
    response_json JSON NULL,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_turn_session FOREIGN KEY (session_id) REFERENCES agent_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_turn_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_agent_turn_user_request (user_id, client_request_id),
    INDEX idx_agent_turn_session_created (session_id, created_at),
    INDEX idx_agent_turn_user_status (user_id, status, updated_at)
);

ALTER TABLE agent_message
    ADD CONSTRAINT fk_agent_message_turn FOREIGN KEY (turn_id) REFERENCES agent_turn(id) ON DELETE SET NULL;
