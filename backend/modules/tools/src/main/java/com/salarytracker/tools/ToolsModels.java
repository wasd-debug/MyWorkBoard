package com.salarytracker.tools;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工具箱接口的传输模型。全部以 {@code Tools} 前缀命名，避免与既有模块中
 * 同名 record（例如 {@code DeletedResource}）在 OpenAPI 里冲突。
 */
public final class ToolsModels {
    private ToolsModels() {
    }

    @Schema(name = "ToolsQuitSmokingSettings")
    public record QuitSmokingSettings(String publicId, String startAt, String timezone, int baselinePerDay,
                                      BigDecimal pricePerPack, int cigarettesPerPack, String currency,
                                      boolean configured, long revision) {
    }

    @Schema(name = "ToolsQuitSmokingSettingsUpdate")
    public record QuitSmokingSettingsUpdate(String startAt, String timezone, Integer baselinePerDay,
                                            BigDecimal pricePerPack, Integer cigarettesPerPack, String currency) {
    }

    @Schema(name = "ToolsQuitSmokingDay")
    public record QuitSmokingDay(String date, int smokedCount, String note, long revision) {
    }

    @Schema(name = "ToolsQuitSmokingDayUpdate")
    public record QuitSmokingDayUpdate(Integer smokedCount, String note) {
    }

    @Schema(name = "ToolsQuitSmokingCraving")
    public record QuitSmokingCraving(String id, String occurredAt, String date, int intensity,
                                     boolean resisted, String trigger, String note, long revision) {
    }

    @Schema(name = "ToolsQuitSmokingCravingCreate")
    public record QuitSmokingCravingCreate(String occurredAt, Integer intensity, Boolean resisted,
                                           String trigger, String note) {
    }

    @Schema(name = "ToolsQuitSmokingCravingUpdate")
    public record QuitSmokingCravingUpdate(Integer intensity, Boolean resisted, String trigger, String note) {
    }

    @Schema(name = "ToolsQuitSmokingSummary")
    public record QuitSmokingSummary(String startAt, int elapsedDays, int cleanDays, int currentStreak,
                                     int longestStreak, int totalSmoked, BigDecimal savedAmount, String currency,
                                     String todayDate, int todaySmoked, int todayCravings, int todayResisted,
                                     boolean todayLogged, long settingsRevision, long todayRevision,
                                     List<QuitSmokingCraving> todayCravingItems) {
    }

    @Schema(name = "ToolsDeletedResource")
    public record DeletedResource(String id) {
    }
}
