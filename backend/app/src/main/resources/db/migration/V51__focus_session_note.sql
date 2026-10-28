-- 专注记录支持「记录想法」：结束后的复盘文字，直接挂在会话上
ALTER TABLE focus_session ADD COLUMN note VARCHAR(1000) NOT NULL DEFAULT '' AFTER device_label;
