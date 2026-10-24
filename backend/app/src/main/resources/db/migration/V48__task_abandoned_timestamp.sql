ALTER TABLE task
    ADD COLUMN abandoned_at TIMESTAMP(3) NULL AFTER completed_at,
    ADD KEY idx_task_user_status_abandoned (user_id, status, abandoned_at);

UPDATE task t
JOIN (
    SELECT task_id, MAX(created_at) AS abandoned_at
    FROM task_activity
    WHERE event_type = 'ABANDONED'
    GROUP BY task_id
) activity ON activity.task_id = t.id
SET t.abandoned_at = activity.abandoned_at
WHERE t.status = 'ABANDONED';
