INSERT INTO holiday (year_key, date, name, is_off) VALUES
    (2026, '2026-01-01', '元旦', TRUE),
    (2026, '2026-01-02', '元旦', TRUE),
    (2026, '2026-01-03', '元旦', TRUE),
    (2026, '2026-01-04', '元旦', FALSE),
    (2026, '2026-02-14', '春节', FALSE),
    (2026, '2026-02-15', '春节', TRUE),
    (2026, '2026-02-16', '春节', TRUE),
    (2026, '2026-02-17', '春节', TRUE),
    (2026, '2026-02-18', '春节', TRUE),
    (2026, '2026-02-19', '春节', TRUE),
    (2026, '2026-02-20', '春节', TRUE),
    (2026, '2026-02-21', '春节', TRUE),
    (2026, '2026-02-22', '春节', TRUE),
    (2026, '2026-02-23', '春节', TRUE),
    (2026, '2026-02-28', '春节', FALSE),
    (2026, '2026-04-04', '清明节', TRUE),
    (2026, '2026-04-05', '清明节', TRUE),
    (2026, '2026-04-06', '清明节', TRUE),
    (2026, '2026-05-01', '劳动节', TRUE),
    (2026, '2026-05-02', '劳动节', TRUE),
    (2026, '2026-05-03', '劳动节', TRUE),
    (2026, '2026-05-04', '劳动节', TRUE),
    (2026, '2026-05-05', '劳动节', TRUE),
    (2026, '2026-05-09', '劳动节', FALSE),
    (2026, '2026-06-19', '端午节', TRUE),
    (2026, '2026-06-20', '端午节', TRUE),
    (2026, '2026-06-21', '端午节', TRUE),
    (2026, '2026-09-20', '国庆节', FALSE),
    (2026, '2026-09-25', '中秋节', TRUE),
    (2026, '2026-09-26', '中秋节', TRUE),
    (2026, '2026-09-27', '中秋节', TRUE),
    (2026, '2026-10-01', '国庆节', TRUE),
    (2026, '2026-10-02', '国庆节', TRUE),
    (2026, '2026-10-03', '国庆节', TRUE),
    (2026, '2026-10-04', '国庆节', TRUE),
    (2026, '2026-10-05', '国庆节', TRUE),
    (2026, '2026-10-06', '国庆节', TRUE),
    (2026, '2026-10-07', '国庆节', TRUE),
    (2026, '2026-10-10', '国庆节', FALSE)
ON DUPLICATE KEY UPDATE
    year_key = VALUES(year_key), name = VALUES(name), is_off = VALUES(is_off);

ALTER TABLE work_record
    MODIFY calc_version VARCHAR(32) NOT NULL DEFAULT 'phase0-v2-day-type';

UPDATE work_record wr
JOIN work_setting ws ON ws.user_id = wr.user_id
LEFT JOIN salary_monthly sm
    ON sm.user_id = wr.user_id AND sm.month_key = DATE_FORMAT(wr.date, '%Y-%m')
LEFT JOIN holiday h ON h.date = wr.date
SET wr.overtime_min = CASE
        WHEN wr.end_time IS NULL THEN 0
        WHEN COALESCE(h.is_off, DAYOFWEEK(wr.date) IN (1, 7)) THEN
            GREATEST(0,
                MOD(TIME_TO_SEC(TIMEDIFF(wr.end_time, wr.start_time)) + 86400, 86400) / 60
                - ws.lunch_min - wr.rest_min)
        WHEN GREATEST(0,
                MOD(TIME_TO_SEC(TIMEDIFF(wr.end_time, wr.start_time)) + 86400, 86400) / 60
                - ws.lunch_min - wr.rest_min) = 0 THEN 0
        ELSE
            GREATEST(0,
                MOD(TIME_TO_SEC(TIMEDIFF(wr.end_time, wr.start_time)) + 86400, 86400) / 60
                - ws.lunch_min - wr.rest_min)
            - GREATEST(0,
                MOD(TIME_TO_SEC(TIMEDIFF(ws.work_end, ws.work_start)) + 86400, 86400) / 60
                - ws.lunch_min)
    END,
    wr.real_hourly_wage = CASE
        WHEN wr.end_time IS NULL
             OR ws.days_per_month <= 0
             OR GREATEST(0,
                    MOD(TIME_TO_SEC(TIMEDIFF(wr.end_time, wr.start_time)) + 86400, 86400) / 60
                    - ws.lunch_min - wr.rest_min) = 0 THEN 0
        ELSE ROUND(
            COALESCE(
                CAST(JSON_UNQUOTE(JSON_EXTRACT(wr.salary_snapshot, '$.salary')) AS DECIMAL(12, 2)),
                CASE ws.basis
                    WHEN 'pre' THEN COALESCE(sm.salary_pre, ws.salary_pre)
                    ELSE COALESCE(sm.salary_post, ws.salary_post)
                END
            ) / ws.days_per_month /
            (GREATEST(0,
                MOD(TIME_TO_SEC(TIMEDIFF(wr.end_time, wr.start_time)) + 86400, 86400) / 60
                - ws.lunch_min - wr.rest_min) / 60),
            2)
    END,
    wr.calc_version = 'phase0-v2-day-type',
    wr.revision = wr.revision + 1
WHERE wr.deleted = FALSE;
