ALTER TABLE ledger_scheduled_task
    ADD COLUMN schedule_mode VARCHAR(16) NOT NULL DEFAULT 'INTERVAL' AFTER enabled,
    ADD COLUMN calendar_rule_json JSON NULL AFTER interval_value;
