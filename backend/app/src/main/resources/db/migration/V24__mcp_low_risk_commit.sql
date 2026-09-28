ALTER TABLE mcp_external_action
    ADD COLUMN commit_status VARCHAR(24) NULL AFTER rejected_at,
    ADD COLUMN commit_summary VARCHAR(500) NULL AFTER commit_status,
    ADD COLUMN commit_result_json JSON NULL AFTER commit_summary,
    ADD COLUMN commit_started_at DATETIME(6) NULL AFTER commit_result_json,
    ADD COLUMN committed_at DATETIME(6) NULL AFTER commit_started_at;

CREATE INDEX idx_mcp_external_action_commit_status
    ON mcp_external_action (token_id, commit_status, created_at);
