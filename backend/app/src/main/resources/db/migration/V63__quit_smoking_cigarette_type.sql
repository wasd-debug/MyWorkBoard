-- 戒烟日记：香烟种类与每支焦油/一氧化碳含量
-- 设计说明：
-- 1) 种类只作为「预设来源」，真正参与计算的是每支焦油/一氧化碳毫克数；
--    用户可以在预设基础上微调（不同品牌差异很大），所以两者都要落库。
-- 2) DECIMAL(5,1) 足够覆盖市售卷烟的 0.1–100 mg 量级，避免浮点误差。
-- 3) 已有行按普通卷烟（11 mg 焦油 / 12 mg 一氧化碳）回填，即中国国标限量附近的常见值，
--    不会让历史用户的「少吸入」统计突然变成 0。

ALTER TABLE quit_smoking_setting
    ADD COLUMN cigarette_type VARCHAR(32) NOT NULL DEFAULT 'regular' AFTER currency,
    ADD COLUMN tar_per_cigarette DECIMAL(5, 1) NOT NULL DEFAULT 11.0 AFTER cigarette_type,
    ADD COLUMN co_per_cigarette DECIMAL(5, 1) NOT NULL DEFAULT 12.0 AFTER tar_per_cigarette;
