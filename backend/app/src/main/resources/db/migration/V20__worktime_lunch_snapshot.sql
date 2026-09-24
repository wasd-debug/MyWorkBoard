ALTER TABLE work_record
    ADD COLUMN lunch_min INT NULL AFTER rest_min;

UPDATE work_record wr
JOIN work_setting ws ON ws.user_id = wr.user_id
SET wr.lunch_min = ws.lunch_min
WHERE wr.lunch_min IS NULL;

UPDATE work_record
SET lunch_min = 90
WHERE lunch_min IS NULL;

ALTER TABLE work_record
    MODIFY COLUMN lunch_min INT NOT NULL DEFAULT 90;
