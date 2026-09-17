CREATE TABLE IF NOT EXISTS migration_reconciliation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    migration_name VARCHAR(120) NOT NULL,
    source_count BIGINT NOT NULL DEFAULT 0,
    target_count BIGINT NOT NULL DEFAULT 0,
    checksum VARCHAR(128) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_migration_reconciliation_name (migration_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS idempotency_key (
    user_id BIGINT NOT NULL,
    op_key VARCHAR(160) NOT NULL,
    response_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, op_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO migration_reconciliation (migration_name, source_count, target_count)
SELECT 'legacy-records-to-work-record', COUNT(*), (SELECT COUNT(*) FROM work_record WHERE user_id = 1)
FROM records
ON DUPLICATE KEY UPDATE source_count = VALUES(source_count), target_count = VALUES(target_count);
