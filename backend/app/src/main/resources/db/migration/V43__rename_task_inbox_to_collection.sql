UPDATE task_list
SET name = '收集箱', revision = revision + 1
WHERE system_key = 'INBOX' AND name <> '收集箱';
