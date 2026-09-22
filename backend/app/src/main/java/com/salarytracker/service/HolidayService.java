package com.salarytracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.entity.KvEntry;
import com.salarytracker.mapper.KvEntryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 法定节假日数据服务
 * 数据源：NateScarlet/holiday-cn（国务院办公厅公告整理，含放假与调休补班）
 * 优先级：kv 缓存 -> 远程刷新 -> jar 内置兜底
 */
@Service
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    /** 远程数据源（按顺序尝试，国内服务器 GitHub 直连可能不通） */
    private static final String[] REMOTE_SOURCES = {
            "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/%d.json",
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
            "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json"
    };
    private static final int MIN_YEAR = 2024;
    private static final int MAX_YEAR = 2100;

    private final ObjectMapper objectMapper;
    private final KvEntryMapper kvEntryMapper;
    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HolidayService(ObjectMapper objectMapper, KvEntryMapper kvEntryMapper) {
        this(objectMapper, kvEntryMapper, null);
    }

    @Autowired
    public HolidayService(ObjectMapper objectMapper, KvEntryMapper kvEntryMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.kvEntryMapper = kvEntryMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取某年节假日（含调休补班）
     *
     * @param year 年份
     * @return {days: {"2026-01-01": {name, off}}, source: cache|remote|builtin|none}
     */
    public HolidayResponse getHolidays(int year) {
        if (jdbcTemplate != null) {
            try {
                List<HolidayRow> rows = jdbcTemplate.query(
                        "SELECT DATE_FORMAT(date, '%Y-%m-%d') date, name, is_off FROM holiday WHERE year_key = ? ORDER BY date",
                        (result, rowNum) -> new HolidayRow(result.getString("date"), result.getString("name"),
                                result.getBoolean("is_off")), year);
                if (!rows.isEmpty()) {
                    Map<String, HolidayDay> days = new LinkedHashMap<>();
                    for (HolidayRow row : rows) {
                        days.put(row.date(), new HolidayDay(row.name(), row.off()));
                    }
                    return new HolidayResponse(true, year, days, HolidaySource.DATABASE);
                }
            } catch (Exception e) {
                log.debug("读取节假日表失败 year={}: {}", year, e.getMessage());
            }
        }

        String cacheKey = "holidays:" + year;
        // 1. kv 缓存
        try {
            KvEntry cached = kvEntryMapper.selectById(cacheKey);
            if (cached != null && cached.getValue() != null && !cached.getValue().isEmpty()) {
                JsonNode days = objectMapper.readTree(cached.getValue());
                Map<String, HolidayDay> normalized = normalize(days);
                persistDatabase(year, normalized);
                return new HolidayResponse(true, year, normalized, HolidaySource.CACHE);
            }
        } catch (Exception e) {
            log.warn("读取节假日缓存失败 year={}: {}", year, e.getMessage());
        }

        // 2. 远程拉取
        String remote = fetchRemote(year);
        if (remote != null) {
            try {
                JsonNode days = objectMapper.readTree(remote);
                String compact = objectMapper.writeValueAsString(days);
                persistDatabase(year, normalize(days));
                try {
                    KvEntry entry = new KvEntry();
                    entry.setKey(cacheKey);
                    entry.setValue(compact);
                    kvEntryMapper.insert(entry);
                } catch (Exception dup) {
                    KvEntry entry = new KvEntry();
                    entry.setKey(cacheKey);
                    entry.setValue(compact);
                    kvEntryMapper.updateById(entry);
                }
                return new HolidayResponse(true, year, normalize(days), HolidaySource.REMOTE);
            } catch (Exception e) {
                log.warn("解析远程节假日数据失败 year={}: {}", year, e.getMessage());
            }
        }

        // 3. jar 内置兜底
        JsonNode builtin = loadBuiltin(year);
        if (builtin != null) {
            persistDatabase(year, normalize(builtin));
            return new HolidayResponse(true, year, normalize(builtin), HolidaySource.BUILTIN);
        }

        // 4. 全部失败：返回空，前端按纯周一~周五计算
        return new HolidayResponse(true, year, Map.of(), HolidaySource.NONE);
    }

    /** 归一化为 {date: {name, off}} */
    private Map<String, HolidayDay> normalize(JsonNode daysNode) {
        Map<String, HolidayDay> out = new LinkedHashMap<>();
        if (daysNode != null && daysNode.isArray()) {
            for (JsonNode d : daysNode) {
                String date = d.path("date").asText("");
                if (date.isEmpty()) continue;
                out.put(date, new HolidayDay(d.path("name").asText(""), d.path("isOffDay").asBoolean(false)));
            }
        }
        return out;
    }

    /** 远程拉取原始 JSON 字符串，失败返回 null */
    private String fetchRemote(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) return null;
        for (String tpl : REMOTE_SOURCES) {
            String url = String.format(tpl, year);
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header("User-Agent", "salary-tracker/1.0")
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(resp.body());
                    if (root.has("days") && root.get("days").isArray() && root.get("days").size() > 0) {
                        log.info("节假日数据拉取成功 year={} source={}", year, url);
                        return root.get("days").toString();
                    }
                }
            } catch (Exception e) {
                log.debug("节假日远程源不可用 {}: {}", url, e.getMessage());
            }
        }
        return null;
    }

    /** 加载 jar 内置数据，无则返回 null */
    private JsonNode loadBuiltin(int year) {
        try {
            ClassPathResource res = new ClassPathResource("holidays/" + year + ".json");
            if (!res.exists()) return null;
            try (InputStream in = res.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                return root.get("days");
            }
        } catch (Exception e) {
            log.warn("读取内置节假日数据失败 year={}: {}", year, e.getMessage());
            return null;
        }
    }

    /** 清除某年缓存（refresh=1 时调用） */
    public void evictCache(int year) {
        try {
            kvEntryMapper.deleteById("holidays:" + year);
        } catch (Exception e) {
            log.warn("清除节假日缓存失败 year={}: {}", year, e.getMessage());
        }
        if (jdbcTemplate != null) {
            try { jdbcTemplate.update("DELETE FROM holiday WHERE year_key = ?", year); }
            catch (Exception e) { log.debug("清除节假日表失败 year={}: {}", year, e.getMessage()); }
        }
    }

    private void persistDatabase(int year, Map<String, HolidayDay> days) {
        if (jdbcTemplate == null) return;
        try {
            for (Map.Entry<String, HolidayDay> entry : days.entrySet()) {
                HolidayDay value = entry.getValue();
                jdbcTemplate.update("INSERT INTO holiday (year_key, date, name, is_off) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE year_key = VALUES(year_key), name = VALUES(name), is_off = VALUES(is_off)",
                        year, entry.getKey(), value.name(), value.off());
            }
        } catch (Exception e) {
            log.debug("写入节假日表失败 year={}: {}", year, e.getMessage());
        }
    }

    /** 校验年份参数 */
    public static int parseYear(String raw) {
        try {
            int y = Integer.parseInt(raw);
            return (y >= 2000 && y <= 2100) ? y : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public enum HolidaySource {
        DATABASE, CACHE, REMOTE, BUILTIN, NONE
    }

    public record HolidayDay(String name, boolean off) {
    }

    public record HolidayResponse(boolean ok, int year, Map<String, HolidayDay> days, HolidaySource source) {
    }

    private record HolidayRow(String date, String name, boolean off) {
    }
}
