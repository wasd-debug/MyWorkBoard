ALTER TABLE ai_model_pricing
    ADD COLUMN pricing_mode VARCHAR(32) NOT NULL DEFAULT 'FLAT' AFTER currency,
    ADD COLUMN off_peak_input_per_million DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER reasoning_per_million,
    ADD COLUMN off_peak_output_per_million DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER off_peak_input_per_million,
    ADD COLUMN off_peak_cache_hit_per_million DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER off_peak_output_per_million,
    ADD COLUMN off_peak_cache_miss_per_million DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER off_peak_cache_hit_per_million,
    ADD COLUMN off_peak_reasoning_per_million DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER off_peak_cache_miss_per_million;

CREATE TABLE ai_environment_pricing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    version INT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    pricing_mode VARCHAR(32) NOT NULL DEFAULT 'FLAT',
    input_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    output_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    cache_hit_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    cache_miss_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    reasoning_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    off_peak_input_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    off_peak_output_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    off_peak_cache_hit_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    off_peak_cache_miss_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    off_peak_reasoning_per_million DECIMAL(20,8) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_environment_pricing_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_ai_environment_pricing_version (user_id, version),
    INDEX idx_ai_environment_pricing_active (user_id, active, version)
);

ALTER TABLE ai_usage
    ADD COLUMN pricing_tier VARCHAR(16) NULL AFTER currency;
