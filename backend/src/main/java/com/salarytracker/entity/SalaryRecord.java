package com.salarytracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 每日打卡记录，对应表 records
 */
@Data
@TableName("records")
public class SalaryRecord {

    /** 日期 YYYY-MM-DD */
    @TableId(value = "date", type = IdType.INPUT)
    private String date;

    /** 实际上班 HH:mm */
    private String start;

    /** 实际下班 HH:mm（可为空串表示仅打卡上班） */
    private String end;

    /** 当日自定义休息分钟 */
    private Integer rest;
}
