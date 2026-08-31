package com.salarytracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 全局设置（单行），对应表 settings
 */
@Data
@TableName("settings")
public class SettingsRow {

    /** 固定为 1 */
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    /** 设置项 JSON 字符串 */
    private String data;
}
