package com.salarytracker.worktime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

public final class WorktimeModels {
    private WorktimeModels() {
    }

    @Schema(name = "WorktimeBasis")
    public enum Basis {
        PRE("pre"), POST("post");

        private final String value;

        Basis(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static Basis from(String value) {
            return "pre".equalsIgnoreCase(value) ? PRE : POST;
        }
    }

    @Schema(name = "MonthlySalary")
    public record MonthlySalary(BigDecimal pre, BigDecimal post) {
    }

    @Schema(name = "WorktimeSettings")
    public record Settings(BigDecimal salaryPre, BigDecimal salaryPost, Basis basis, String workStart,
                           String workEnd, int lunchMin, BigDecimal daysPerMonth, boolean autoDays,
                           Map<String, MonthlySalary> salaries, long revision) {
    }

    @Schema(name = "WorktimeSettingsUpdate")
    public record SettingsUpdate(BigDecimal salaryPre, BigDecimal salaryPost, Basis basis, String workStart,
                                 String workEnd, Integer lunchMin, BigDecimal daysPerMonth, Boolean autoDays,
                                 Map<String, MonthlySalary> salaries) {
    }

    @Schema(name = "WorktimeRecordCommand")
    public record RecordCommand(String date, String start, String end, Integer rest, String note) {
    }

    @Schema(name = "WorktimeRecordPreview")
    public record RecordPreview(String date, String start, String end, int rest, int overtimeMin,
                                BigDecimal realHourlyWage, String note) {
    }

    @Schema(name = "WorktimeRecord")
    public record WorkRecord(long id, String date, String start, String end, int rest, int overtimeMin,
                             BigDecimal realHourlyWage, String note, String calcVersion, String timezone,
                             long revision) {
    }

    @Schema(name = "DeletedResource")
    public record DeletedResource(long id, long revision, boolean deleted) {
    }
}
