ALTER TABLE agent_session
    ADD COLUMN queue_revision BIGINT NOT NULL DEFAULT 0 AFTER pinned_at;

ALTER TABLE agent_turn
    ADD COLUMN queue_position BIGINT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN retry_of_turn_id VARCHAR(64) NULL AFTER client_request_id,
    ADD INDEX idx_agent_turn_session_queue (session_id, status, queue_position, created_at),
    ADD UNIQUE KEY uk_agent_turn_retry_once (retry_of_turn_id),
    ADD CONSTRAINT fk_agent_turn_retry FOREIGN KEY (retry_of_turn_id) REFERENCES agent_turn(id) ON DELETE SET NULL;

UPDATE agent_turn
SET queue_position = CAST(UNIX_TIMESTAMP(created_at) * 1000000 AS UNSIGNED)
WHERE queue_position = 0;
