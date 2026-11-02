-- 倒数纪念日：补齐卡片样式、图标、显示单位、提醒/重复、归档、农历与系统内置标识
ALTER TABLE countdown
    ADD COLUMN icon_type VARCHAR(8) NOT NULL DEFAULT 'EMOJI' AFTER title,
    ADD COLUMN icon_value VARCHAR(32) NOT NULL DEFAULT '⏳' AFTER icon_type,
    ADD COLUMN icon_color VARCHAR(16) NOT NULL DEFAULT '#f2b13c' AFTER icon_value,
    ADD COLUMN cover VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER kind,
    ADD COLUMN cover_image MEDIUMTEXT NULL AFTER cover,
    ADD COLUMN unit VARCHAR(16) NOT NULL DEFAULT 'DAY' AFTER cover_image,
    ADD COLUMN precise_seconds TINYINT(1) NOT NULL DEFAULT 0 AFTER unit,
    ADD COLUMN remind VARCHAR(64) NOT NULL DEFAULT '' AFTER precise_seconds,
    ADD COLUMN repeat_rule VARCHAR(32) NOT NULL DEFAULT 'NONE' AFTER remind,
    ADD COLUMN display_rule VARCHAR(16) NOT NULL DEFAULT 'SMART' AFTER repeat_rule,
    ADD COLUMN archived TINYINT(1) NOT NULL DEFAULT 0 AFTER display_rule,
    ADD COLUMN calendar_type VARCHAR(8) NOT NULL DEFAULT 'SOLAR' AFTER archived,
    ADD COLUMN lunar_date VARCHAR(16) NULL AFTER calendar_type,
    ADD COLUMN builtin_key VARCHAR(32) NOT NULL DEFAULT '' AFTER lunar_date,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER builtin_key;

CREATE INDEX idx_countdown_builtin ON countdown (user_id, builtin_key);
CREATE INDEX idx_countdown_archived ON countdown (user_id, archived, target_date);
