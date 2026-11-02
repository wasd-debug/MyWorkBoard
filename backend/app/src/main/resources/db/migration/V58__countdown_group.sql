-- 倒数纪念日：把原来的「类型」升级成可增删改的分组实体
CREATE TABLE countdown_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    icon VARCHAR(16) NOT NULL DEFAULT '📁',
    color VARCHAR(16) NOT NULL DEFAULT '#4d78db',
    sort_order INT NOT NULL DEFAULT 0,
    builtin_key VARCHAR(16) NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_countdown_group_public (public_id),
    KEY idx_countdown_group_user (user_id, deleted_at, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 已有账号补上四个默认分组，与原来的四种类型一一对应
INSERT INTO countdown_group (public_id, user_id, name, icon, color, sort_order, builtin_key)
SELECT UUID(), u.id, '纪念日', '🎉', '#e2685f', 0, 'ANNIVERSARY' FROM app_user u;

INSERT INTO countdown_group (public_id, user_id, name, icon, color, sort_order, builtin_key)
SELECT UUID(), u.id, '倒数日', '⏳', '#4d78db', 1, 'COUNTDOWN' FROM app_user u;

INSERT INTO countdown_group (public_id, user_id, name, icon, color, sort_order, builtin_key)
SELECT UUID(), u.id, '生日', '🎂', '#ef7f96', 2, 'BIRTHDAY' FROM app_user u;

INSERT INTO countdown_group (public_id, user_id, name, icon, color, sort_order, builtin_key)
SELECT UUID(), u.id, '节日', '🎊', '#f2994a', 3, 'FESTIVAL' FROM app_user u;

ALTER TABLE countdown
    ADD COLUMN group_id VARCHAR(36) NOT NULL DEFAULT '' AFTER kind;

-- 历史卡片按 kind 落到对应分组
UPDATE countdown c
    JOIN countdown_group g ON g.user_id = c.user_id AND g.builtin_key = c.kind AND g.deleted_at IS NULL
SET c.group_id = g.public_id
WHERE c.group_id = '';

-- 兜底：kind 不在四类里的，落到该用户第一个分组
UPDATE countdown c
    JOIN countdown_group g ON g.user_id = c.user_id AND g.deleted_at IS NULL AND g.sort_order = 0
SET c.group_id = g.public_id
WHERE c.group_id = '';

CREATE INDEX idx_countdown_group ON countdown (user_id, group_id);
