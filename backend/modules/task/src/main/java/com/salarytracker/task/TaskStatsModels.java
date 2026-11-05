package com.salarytracker.task;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 任务域统计页（总览 / 任务 / 专注）的数据结构。
 * 全部由服务端 SQL 聚合真实数据，前端不再自行推算。
 */
public final class TaskStatsModels {
    private TaskStatsModels() {
    }

    @Schema(name = "TaskStatsPoint")
    public record Point(String date, String label, double value) {
    }

    @Schema(name = "TaskStatsRing")
    public record Ring(String date, String label, int percent, boolean today, long count) {
    }

    @Schema(name = "TaskStatsOverview")
    public record Overview(long totalTasks, long completedTasks, long totalLists, long useDays, int percentile,
                           long todayCompleted, long todayPomodoro, long todayFocusMinutes,
                           long allCompleted, long allPomodoro, long allFocusMinutes,
                           long achievement,
                           List<Point> achievementTrend, List<Point> completedTrend, List<Point> rateTrend,
                           List<Point> pomodoroTrend, List<Point> focusTrend,
                           List<Ring> weekRings) {
    }

    @Schema(name = "TaskStatsSlice")
    public record Slice(String id, String name, String color, long value) {
    }

    @Schema(name = "TaskStatsBucket")
    public record Bucket(String key, String label, long value, String color) {
    }

    @Schema(name = "TaskStatsTaskPage")
    public record TaskPageStats(String mode, String from, String to, String label,
                                long completed, long total, int rate,
                                long prevCompleted, int prevRate,
                                long overdueCompleted, long onTimeCompleted,
                                long noDueCompleted, long undone,
                                String groupMode, List<Slice> groups) {
    }

    @Schema(name = "TaskStatsFocusRecord")
    public record FocusRecord(String publicId, String date, String startTime, String endTime, String title,
                              long minutes, String targetType) {
    }

    @Schema(name = "TaskStatsTimelineDay")
    public record TimelineDay(String date, String label, boolean today, List<Double> hours) {
    }

    @Schema(name = "TaskStatsHeatDay")
    public record HeatDay(String date, long minutes) {
    }

    @Schema(name = "TaskStatsFocusPage")
    public record FocusPage(long todayPomodoro, long todayMinutes, long totalPomodoro, long totalMinutes,
                            String detailDate, String detailLabel,
                            String groupMode, List<Slice> groups,
                            List<FocusRecord> records,
                            String trendMode, String trendLabel, List<Point> trend,
                            String timelineLabel, List<TimelineDay> timeline,
                            String monthLabel, List<Point> bestHours,
                            List<HeatDay> heatmap, int year) {
    }
}
