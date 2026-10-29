-- 完成量习惯要带单位（喝水 毫升、体重 kg），详情面板的「月完成量/总完成量」靠它显示
ALTER TABLE habit ADD COLUMN amount_unit VARCHAR(16) NOT NULL DEFAULT '' AFTER auto_popup_log;
