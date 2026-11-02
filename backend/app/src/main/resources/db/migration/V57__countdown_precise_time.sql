-- 倒数纪念日：精确到秒时按「精确时刻」倒计时，未设置则按当天零点
ALTER TABLE countdown
    ADD COLUMN precise_time VARCHAR(5) NOT NULL DEFAULT '' AFTER precise_seconds;
