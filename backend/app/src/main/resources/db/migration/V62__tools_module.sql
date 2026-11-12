-- 工具箱：戒烟日记（设置 / 每日打卡 / 渴望事件）
-- 设计说明：
-- 1) 设置是「每用户一行」的配置，与日记数据的生命周期不同，单独一张表；
-- 2) 每日打卡是「每用户每自然日一行」，以 (user_id, day_date) 幂等；
-- 3) 渴望事件一天可能多条，需要独立主键与范围查询，且允许软删除，因此单独建表。
-- 每日打卡不冗余保存渴望计数，统一由 quit_smoking_craving 聚合，避免两处写入漂移。

CREATE TABLE quit_smoking_setting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    start_at TIMESTAMP(3) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    baseline_per_day INT NOT NULL DEFAULT 20,
    price_per_pack DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    cigarettes_per_pack INT NOT NULL DEFAULT 20,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quit_smoking_setting_public (public_id),
    UNIQUE KEY uk_quit_smoking_setting_user (user_id),
    CONSTRAINT fk_quit_smoking_setting_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE quit_smoking_day (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    day_date DATE NOT NULL,
    smoked_count INT NOT NULL DEFAULT 0,
    note VARCHAR(500) NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quit_smoking_day_public (public_id),
    UNIQUE KEY uk_quit_smoking_day_user_date (user_id, day_date),
    CONSTRAINT fk_quit_smoking_day_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE quit_smoking_craving (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    day_date DATE NOT NULL,
    intensity TINYINT NOT NULL DEFAULT 3,
    resisted BOOLEAN NOT NULL DEFAULT TRUE,
    trigger_key VARCHAR(64) NOT NULL DEFAULT '',
    note VARCHAR(500) NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quit_smoking_craving_public (public_id),
    KEY idx_quit_smoking_craving_user_day (user_id, day_date, occurred_at),
    CONSTRAINT fk_quit_smoking_craving_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO permission (code, module, action) VALUES
    ('tools:read', 'tools', 'read'),
    ('tools:write', 'tools', 'write');

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN ('tools:read', 'tools:write')
WHERE r.code IN ('ADMIN', 'USER');
