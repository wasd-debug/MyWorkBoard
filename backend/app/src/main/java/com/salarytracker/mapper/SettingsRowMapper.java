package com.salarytracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.salarytracker.entity.SettingsRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

public interface SettingsRowMapper extends BaseMapper<SettingsRow> {

    /**
     * 单行配置 upsert：INSERT ... ON DUPLICATE KEY UPDATE
     */
    @Insert("INSERT INTO settings (id, data) VALUES (#{id}, #{data}) " +
            "ON DUPLICATE KEY UPDATE data = VALUES(data)")
    @Options(useGeneratedKeys = false)
    int upsertData(Integer id, String data);
}
