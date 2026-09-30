ALTER TABLE mcp_tool_call
    DROP FOREIGN KEY fk_mcp_tool_call_token;

ALTER TABLE mcp_tool_call
    MODIFY COLUMN token_id CHAR(36) NULL;

ALTER TABLE mcp_tool_call
    ADD CONSTRAINT fk_mcp_tool_call_token FOREIGN KEY (token_id)
        REFERENCES mcp_personal_token(id) ON DELETE SET NULL;

ALTER TABLE mcp_protocol_event
    DROP FOREIGN KEY fk_mcp_protocol_event_token;

ALTER TABLE mcp_protocol_event
    ADD CONSTRAINT fk_mcp_protocol_event_token FOREIGN KEY (token_id)
        REFERENCES mcp_personal_token(id) ON DELETE SET NULL;
