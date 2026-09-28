CREATE TABLE ai_usage_budget (
    user_id BIGINT PRIMARY KEY,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    daily_limit DECIMAL(24,12) NULL,
    monthly_limit DECIMAL(24,12) NULL,
    single_request_limit DECIMAL(24,12) NULL,
    token_limit BIGINT NULL,
    enforcement_mode VARCHAR(16) NOT NULL DEFAULT 'WARN',
    alert_at_50 BOOLEAN NOT NULL DEFAULT TRUE,
    alert_at_80 BOOLEAN NOT NULL DEFAULT TRUE,
    alert_at_100 BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_usage_budget_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE ai_usage_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    alert_type VARCHAR(32) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    threshold_percent INT NOT NULL,
    current_value DECIMAL(24,12) NOT NULL,
    limit_value DECIMAL(24,12) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    detail VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_usage_alert_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_ai_usage_alert (user_id, alert_type, period_key, threshold_percent),
    INDEX idx_ai_usage_alert_user_created (user_id, created_at)
);
