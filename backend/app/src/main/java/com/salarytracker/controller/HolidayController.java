package com.salarytracker.controller;

import com.salarytracker.service.HolidayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 法定节假日接口
 */
@RestController
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    /** GET /api/holidays?year=2026 -> {ok, year, days, source} */
    @GetMapping("/api/holidays")
    public ResponseEntity<Map<String, Object>> holidays(@RequestParam(value = "year", required = false) String year,
                                                        @RequestParam(value = "refresh", required = false) String refresh) {
        int y = HolidayService.parseYear(year == null ? "" : year);
        if (y < 0) {
            Map<String, Object> err = Map.of("error", "year 参数非法，应为 2000-2100 之间的整数");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
        // refresh=1 时先清缓存再取（数据源发布新公告后手动刷新用）
        if ("1".equals(refresh) || "true".equalsIgnoreCase(refresh)) {
            holidayService.evictCache(y);
        }
        return ResponseEntity.ok(holidayService.getHolidays(y));
    }
}
