package com.salarytracker.ledger;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Internal JDBC row adapter. It never crosses a service or HTTP boundary. */
final class DbRow {
    private final Map<String, ?> values;

    private DbRow(Map<String, ?> values) {
        this.values = values;
    }

    static List<DbRow> query(JdbcTemplate jdbc, String sql, Object... args) {
        List<DbRow> rows = new ArrayList<>();
        for (Map<String, ?> row : jdbc.queryForList(sql, args)) rows.add(new DbRow(row));
        return rows;
    }

    static <T> List<T> query(JdbcTemplate jdbc, String sql, Class<T> elementType, Object... args) {
        return jdbc.queryForList(sql, elementType, args);
    }

    static DbRow one(JdbcTemplate jdbc, String sql, Object... args) {
        List<DbRow> rows = query(jdbc, sql, args);
        if (rows.isEmpty()) throw new IllegalArgumentException("数据不存在");
        return rows.get(0);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public Object getOrDefault(String key, Object fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value;
    }

    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    DbRow copy() {
        return new DbRow(new LinkedHashMap<>(values));
    }
}
