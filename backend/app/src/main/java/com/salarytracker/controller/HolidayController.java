package com.salarytracker.controller;

import com.salarytracker.service.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 法定节假日接口
 */
@RestController
@Tag(name = "Holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    /** GET /api/v1/holidays?year=2026 -> typed holiday response. */
    @GetMapping("/api/v1/holidays")
    @Operation(operationId = "getHolidays")
    public HolidayService.HolidayResponse holidays(@RequestParam(value = "year", required = false) String year,
                                                    @RequestParam(value = "refresh", required = false) String refresh) {
        int y = HolidayService.parseYear(year == null ? "" : year);
        if (y < 0) {
            throw new IllegalArgumentException("year 参数非法，应为 2000-2100 之间的整数");
        }
        // refresh=1 时先清缓存再取（数据源发布新公告后手动刷新用）
        if ("1".equals(refresh) || "true".equalsIgnoreCase(refresh)) {
            holidayService.evictCache(y);
        }
        return holidayService.getHolidays(y);
    }
}
