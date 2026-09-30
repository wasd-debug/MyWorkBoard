ALTER TABLE mcp_personal_token
    ADD COLUMN permission_template VARCHAR(24) NOT NULL DEFAULT 'READ_ONLY' AFTER scopes,
    ADD COLUMN high_risk_policy VARCHAR(24) NOT NULL DEFAULT 'APPROVAL_ONLY' AFTER permission_template,
    ADD COLUMN rate_limit_per_minute INT NOT NULL DEFAULT 120 AFTER high_risk_policy;
