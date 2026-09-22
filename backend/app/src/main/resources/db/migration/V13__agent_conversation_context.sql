CREATE TABLE agent_session (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_session_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX idx_agent_session_user_updated (user_id, updated_at)
);

CREATE TABLE agent_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id) REFERENCES agent_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_message_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX idx_agent_message_session_id (session_id, id),
    INDEX idx_agent_message_user_created (user_id, created_at)
);
