-- 首页小组件 / 随身助手这类「临时对话」不应进入 AI 页面的会话列表。
-- ephemeral=1 表示可被清理、默认不出现在列表里；用户点「保存会话」后置为 0。
ALTER TABLE agent_session
    ADD COLUMN ephemeral TINYINT(1) NOT NULL DEFAULT 0 AFTER title;

ALTER TABLE agent_session
    ADD INDEX idx_agent_session_user_ephemeral (user_id, ephemeral, updated_at);
