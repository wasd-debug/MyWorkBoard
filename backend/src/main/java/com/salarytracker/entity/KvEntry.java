package com.salarytracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通用键值存储，对应表 kv（与旧版结构保持一致）
 */
@Data
@TableName("kv")
public class KvEntry {

    /** 键（key 为 MySQL 保留字，映射时加反引号） */
    @TableId(value = "`key`", type = IdType.INPUT)
    private String key;

    /** 值 */
    private String value;
}
