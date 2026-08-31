-- 由旧版 SQLite 数据迁移生成（sqlite_to_mysql.py）
-- 源文件: data/salary-latest.db

INSERT INTO settings (id, data) VALUES (1, '{"workStart": "08:30", "workEnd": "17:30", "lunchMin": 120, "daysPerMonth": 20, "salaryPre": 11650, "salaryPost": 8750, "basis": "pre"}') ON DUPLICATE KEY UPDATE data = VALUES(data);
INSERT INTO records (date, start, end, rest) VALUES ('2026-08-24', '08:30', '19:00', 0) ON DUPLICATE KEY UPDATE start = VALUES(start), end = VALUES(end), rest = VALUES(rest);
INSERT INTO records (date, start, end, rest) VALUES ('2026-08-25', '08:30', '19:00', 60) ON DUPLICATE KEY UPDATE start = VALUES(start), end = VALUES(end), rest = VALUES(rest);
INSERT INTO records (date, start, end, rest) VALUES ('2026-08-26', '08:30', '19:30', 60) ON DUPLICATE KEY UPDATE start = VALUES(start), end = VALUES(end), rest = VALUES(rest);
INSERT INTO records (date, start, end, rest) VALUES ('2026-08-27', '08:30', '', 0) ON DUPLICATE KEY UPDATE start = VALUES(start), end = VALUES(end), rest = VALUES(rest);
