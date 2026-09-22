package com.salarytracker.worktime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

final class WorkdayCalendar {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    WorkdayCalendar(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    boolean isOffDay(LocalDate date) {
        List<Boolean> overrides = jdbcTemplate.query(
                "SELECT is_off FROM holiday WHERE date = ?",
                (result, rowNum) -> result.getBoolean("is_off"), date);
        if (!overrides.isEmpty()) return overrides.get(0);

        Boolean builtin = builtinOverride(date);
        if (builtin != null) return builtin;

        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private Boolean builtinOverride(LocalDate date) {
        ClassPathResource resource = new ClassPathResource("holidays/" + date.getYear() + ".json");
        if (!resource.exists()) return null;
        try (InputStream input = resource.getInputStream()) {
            JsonNode days = objectMapper.readTree(input).path("days");
            for (JsonNode day : days) {
                if (date.toString().equals(day.path("date").asText())) {
                    return day.path("isOffDay").asBoolean(false);
                }
            }
        } catch (Exception ignored) {
            // Fall back to the natural weekend when bundled calendar data cannot be read.
        }
        return null;
    }
}
