ALTER TABLE habit_checkin
    ADD COLUMN skip_reason VARCHAR(200) NOT NULL DEFAULT '' AFTER note;
