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
                                      String cigaretteType, BigDecimal tarPerCigarette, BigDecimal coPerCigarette,
                                      boolean configured, long revision) {
    }

    @Schema(name = "ToolsQuitSmokingSettingsUpdate")
    public record QuitSmokingSettingsUpdate(String startAt, String timezone, Integer baselinePerDay,
                                            BigDecimal pricePerPack, Integer cigarettesPerPack, String currency,
                                            String cigaretteType, BigDecimal tarPerCigarette,
                                            BigDecimal coPerCigarette) {
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

    /** 焦油与一氧化碳：每支含量 + 今日吸入 + 累计少吸入（mg）。 */
    @Schema(name = "ToolsQuitSmokingSubstance")
    public record QuitSmokingSubstance(String cigaretteType, BigDecimal tarPerCigarette, BigDecimal coPerCigarette,
                                       BigDecimal todayTarMg, BigDecimal todayCoMg, BigDecimal avoidedTarMg,
                                       BigDecimal avoidedCoMg) {
    }

    /** 恢复里程碑中的一个节点。{@code progress} 为 0–1，未达成时表示已完成的比例。 */
    @Schema(name = "ToolsQuitSmokingMilestone")
    public record QuitSmokingMilestone(String key, String category, String label, String detail, long atMinutes,
                                       String atLabel, boolean reached, String reachedAt, long remainingMinutes,
                                       double progress, String encouragement) {
    }

    /** 倒计时视图：下一个节点 + 总进度 + 全部节点列表。 */
    @Schema(name = "ToolsQuitSmokingRecovery")
    public record QuitSmokingRecovery(long elapsedMinutes, String nextMilestoneKey, String nextMilestoneLabel,
                                      long nextRemainingMinutes, double nextProgress, int reachedCount,
                                      int totalCount, String encouragement,
                                      List<QuitSmokingMilestone> milestones) {
    }

    @Schema(name = "ToolsQuitSmokingSummary")
    public record QuitSmokingSummary(String startAt, int elapsedDays, int cleanDays, int currentStreak,
                                     int longestStreak, int totalSmoked, BigDecimal savedAmount, String currency,
                                     String todayDate, int todaySmoked, int todayCravings, int todayResisted,
                                     boolean todayLogged, long settingsRevision, long todayRevision,
                                     List<QuitSmokingCraving> todayCravingItems, QuitSmokingSubstance substance,
                                     QuitSmokingRecovery recovery) {
    }

    @Schema(name = "ToolsDeletedResource")
    public record DeletedResource(String id) {
    }
}
