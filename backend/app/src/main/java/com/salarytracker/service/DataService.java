package com.salarytracker.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.entity.SalaryRecord;
import com.salarytracker.entity.SettingsRow;
import com.salarytracker.mapper.SalaryRecordMapper;
import com.salarytracker.mapper.SettingsRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据读写服务
 * 与旧版 Flask 行为完全一致：
 *  - GET /api/data  返回 {settings, records} 整体快照
 *  - PUT /api/data  接收 {settings, records} 整体快照，整体替换存储
 */
@Service
public class DataService {

    private final ObjectMapper objectMapper;
    private final SettingsRowMapper settingsRowMapper;
    private final SalaryRecordMapper salaryRecordMapper;

    public DataService(ObjectMapper objectMapper,
                       SettingsRowMapper settingsRowMapper,
                       SalaryRecordMapper salaryRecordMapper) {
        this.objectMapper = objectMapper;
        this.settingsRowMapper = settingsRowMapper;
        this.salaryRecordMapper = salaryRecordMapper;
    }

    /** 读取整体数据快照 */
    public Map<String, Object> readData() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", readSettings());
        result.put("records", readRecords());
        return result;
    }

    /** 读取设置（JSON -> Map），无记录时返回空 Map */
    public Map<String, Object> readSettings() {
        SettingsRow row = settingsRowMapper.selectById(1);
        if (row == null || row.getData() == null || row.getData().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(row.getData(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 读取全部打卡记录 -> { "YYYY-MM-DD": {start, end, rest} } */
    public Map<String, Object> readRecords() {
        Map<String, Object> records = new LinkedHashMap<>();
        for (SalaryRecord r : salaryRecordMapper.selectList(null)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("start", r.getStart());
            item.put("end", r.getEnd());
            item.put("rest", r.getRest() == null ? 0 : r.getRest());
            records.put(r.getDate(), item);
        }
        return records;
    }

    /**
     * 整体写入数据快照
     *
     * @param payload 已校验的 {settings, records}
     * @return 写入的记录条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int writeData(Map<String, Object> payload) {
        Map<String, Object> settings = castMap(payload.get("settings"));
        Map<String, Object> records = castMap(payload.get("records"));

        // 1. 设置：JSON 序列化后 upsert 到单行
        try {
            settingsRowMapper.upsertData(1, objectMapper.writeValueAsString(settings));
        } catch (Exception e) {
            throw new IllegalArgumentException("设置序列化失败", e);
        }

        // 2. 记录：整体替换（与旧版 DELETE + 批量 INSERT 一致）
        salaryRecordMapper.delete(new QueryWrapper<>());
        List<SalaryRecord> list = new ArrayList<>(records.size());
        for (Map.Entry<String, Object> e : records.entrySet()) {
            Map<String, Object> v = castMap(e.getValue());
            SalaryRecord rec = new SalaryRecord();
            rec.setDate(e.getKey());
            rec.setStart(String.valueOf(v.get("start")));
            rec.setEnd(String.valueOf(v.get("end")));
            rec.setRest(toRest(v.get("rest")));
            list.add(rec);
        }
        if (!list.isEmpty()) {
            for (SalaryRecord rec : list) {
                salaryRecordMapper.insert(rec);
            }
        }
        return list.size();
    }

    /** 校验并规范化 PUT 请求体，非法时抛出 IllegalArgumentException */
    public static Map<String, Object> validate(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("请求体必须是 JSON 对象");
        }
        Object settings = payload.get("settings");
        if (settings != null && !(settings instanceof Map)) {
            throw new IllegalArgumentException("settings 必须是对象");
        }
        Object records = payload.get("records");
        if (records != null && !(records instanceof Map)) {
            throw new IllegalArgumentException("records 必须是对象");
        }
        Map<String, Object> recs = castMap(records);
        for (Map.Entry<String, Object> e : recs.entrySet()) {
            Object val = e.getValue();
            if (!(val instanceof Map)) {
                throw new IllegalArgumentException("记录格式非法");
            }
            Map<?, ?> m = (Map<?, ?>) val;
            Object s = m.get("start");
            Object en = m.get("end");
            if (!(s instanceof String) || !(en instanceof String)) {
                throw new IllegalArgumentException("记录缺少 start/end");
            }
            // rest 非数值一律按 0 处理（与旧版一致）
        }
        return payload;
    }

    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        return new LinkedHashMap<>();
    }

    private static int toRest(Object o) {
        if (o == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(o)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
