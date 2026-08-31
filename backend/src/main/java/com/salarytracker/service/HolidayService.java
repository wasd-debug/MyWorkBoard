package com.salarytracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.entity.KvEntry;
import com.salarytracker.mapper.KvEntryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HolidayService(ObjectMapper objectMapper, KvEntryMapper kvEntryMapper) {
        this.objectMapper = objectMapper;
        this.kvEntryMapper = kvEntryMapper;
    }

    /**
     * 获取某年节假日（含调休补班）
     *
     * @param year 年份
     * @return {days: {"2026-01-01": {name, off}}, source: cache|remote|builtin|none}
     */
    public Map<String, Object> getHolidays(int year) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);

        String cacheKey = "holidays:" + year;
        // 1. kv 缓存
        try {
            KvEntry cached = kvEntryMapper.selectById(cacheKey);
            if (cached != null && cached.getValue() != null && !cached.getValue().isEmpty()) {
                JsonNode days = objectMapper.readTree(cached.getValue());
                result.put("days", normalize(days));
                result.put("source", "cache");
                result.put("ok", true);
                return result;
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
                result.put("days", normalize(days));
                result.put("source", "remote");
                result.put("ok", true);
                return result;
            } catch (Exception e) {
                log.warn("解析远程节假日数据失败 year={}: {}", year, e.getMessage());
            }
        }

        // 3. jar 内置兜底
        JsonNode builtin = loadBuiltin(year);
        if (builtin != null) {
            result.put("days", normalize(builtin));
            result.put("source", "builtin");
            result.put("ok", true);
            return result;
        }

        // 4. 全部失败：返回空，前端按纯周一~周五计算
        result.put("days", new LinkedHashMap<String, Object>());
        result.put("source", "none");
        result.put("ok", true);
        return result;
    }

    /** 归一化为 {date: {name, off}} */
    private Map<String, Object> normalize(JsonNode daysNode) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (daysNode != null && daysNode.isArray()) {
            for (JsonNode d : daysNode) {
                String date = d.path("date").asText("");
                if (date.isEmpty()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", d.path("name").asText(""));
                item.put("off", d.path("isOffDay").asBoolean(false));
                out.put(date, item);
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
}
