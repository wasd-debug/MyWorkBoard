INSERT IGNORE INTO role (id, code, name) VALUES
    (1, 'ADMIN', '管理员'),
    (2, 'USER', '用户');

INSERT IGNORE INTO permission (code, module, action) VALUES
    ('worktime:read', 'worktime', 'read'),
    ('worktime:write', 'worktime', 'write'),
    ('audit:read', 'audit', 'read');

INSERT IGNORE INTO app_user (id, username, password_hash, nickname, timezone, status)
VALUES (1, 'admin', '!', '管理员', 'Asia/Shanghai', 'ACTIVE');

INSERT IGNORE INTO user_role (user_id, role_id) VALUES (1, 1);
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission WHERE code IN ('worktime:read', 'worktime:write', 'audit:read');
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT 2, id FROM permission WHERE code IN ('worktime:read', 'worktime:write', 'audit:read');

INSERT INTO work_setting (user_id, salary_pre, salary_post, basis, work_start, work_end, lunch_min, days_per_month, auto_days)
SELECT 1,
       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.data, '$.salaryPre')) AS DECIMAL(12,2)), 0),
       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.data, '$.salaryPost')) AS DECIMAL(12,2)), 0),
       COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(s.data, '$.basis')), ''), 'post'),
       COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(s.data, '$.workStart')), ''), '09:00:00'),
       COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(s.data, '$.workEnd')), ''), '18:00:00'),
       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.data, '$.lunchMin')) AS UNSIGNED), 90),
       COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.data, '$.daysPerMonth')) AS DECIMAL(6,2)), 21.75),
       CASE LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.data, '$.autoDays')), 'true')) WHEN 'false' THEN 0 WHEN '0' THEN 0 ELSE 1 END
FROM settings s WHERE s.id = 1 AND JSON_VALID(s.data)
ON DUPLICATE KEY UPDATE
    salary_pre = VALUES(salary_pre), salary_post = VALUES(salary_post), basis = VALUES(basis),
    work_start = VALUES(work_start), work_end = VALUES(work_end), lunch_min = VALUES(lunch_min),
    days_per_month = VALUES(days_per_month), auto_days = VALUES(auto_days);

INSERT INTO work_record (user_id, date, start_time, end_time, rest_min, revision)
SELECT 1,
       STR_TO_DATE(r.date, '%Y-%m-%d'),
       STR_TO_DATE(r.start, '%H:%i'),
       CASE WHEN r.end = '' THEN NULL ELSE STR_TO_DATE(r.end, '%H:%i') END,
       GREATEST(COALESCE(r.rest, 0), 0),
       1
FROM records r
ON DUPLICATE KEY UPDATE
    start_time = VALUES(start_time), end_time = VALUES(end_time), rest_min = VALUES(rest_min),
    deleted = FALSE, revision = revision + 1;
