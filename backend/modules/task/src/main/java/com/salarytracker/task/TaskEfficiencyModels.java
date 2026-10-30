package com.salarytracker.task;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public final class TaskEfficiencyModels {
    private TaskEfficiencyModels() {
    }

    @Schema(name = "FocusSettings")
    public record FocusSettings(int focusMinutes, int shortBreakMinutes, int longBreakMinutes,
                                int longBreakInterval, boolean autoStartBreak, boolean autoStartNext,
                                long revision) {
    }

    @Schema(name = "FocusSettingsCommand")
    public record FocusSettingsCommand(Integer focusMinutes, Integer shortBreakMinutes, Integer longBreakMinutes,
                                       Integer longBreakInterval, Boolean autoStartBreak, Boolean autoStartNext) {
    }

    @Schema(name = "FocusSession")
    public record FocusSession(String publicId, String taskId, String taskTitle, int plannedMinutes,
                               int actualMinutes, String startedAt, String plannedEndAt, String endedAt,
                               String status, String deviceLabel, String note, long revision) {
    }

    /** 补记时可以直接给 startedAt / endedAt（ISO-8601），时长自动按差值算。 */
    @Schema(name = "FocusStartCommand")
    public record FocusStartCommand(String taskId, Integer plannedMinutes, String deviceLabel, String requestedDate,
                                    String startedAt, String endedAt, String note) {
    }

    /** 专注记录改「想法」「关联任务」或「起止时间」。
     *  taskId 传空串表示解除关联，传 null 表示不改；startedAt / endedAt 同理，null 表示不改。 */
    @Schema(name = "FocusSessionUpdateCommand")
    public record FocusSessionUpdateCommand(String taskId, String note, String startedAt, String endedAt) {
    }

    @Schema(name = "FocusFinishCommand")
    public record FocusFinishCommand(String status) {
    }

    @Schema(name = "FocusStats")
    public record FocusStats(long todayCount, long todayMinutes, long weekCount, long weekMinutes,
                             long monthCount, long monthMinutes, long totalCount, long totalMinutes,
                             List<FocusDaily> recentDays) {
    }

    public record FocusDaily(String date, long count, long minutes) {
    }

    @Schema(name = "Habit")
    public record Habit(String publicId, String name, String icon, String color, String frequency,
                        int targetCount, List<Integer> customDays, String remindAt, String startDate,
                        boolean archived, int sortOrder, long revision,
                        String groupKey, Integer goalDays, String checkStyle, String targetType,
                        int dailyTimes, boolean autoPopupLog, String amountUnit,
                        int intervalDays, String checkMode, int perAmount, boolean showInToday) {
    }

    @Schema(name = "HabitCommand")
    public record HabitCommand(String name, String icon, String color, String frequency, Integer targetCount,
                               List<Integer> customDays, String remindAt, String startDate, Boolean archived,
                               Integer sortOrder, String groupKey, Integer goalDays, String checkStyle,
                               String targetType, Integer dailyTimes, Boolean autoPopupLog, String amountUnit,
                               Integer intervalDays, String checkMode, Integer perAmount, Boolean showInToday) {
    }

    @Schema(name = "HabitCheckin")
    public record HabitCheckin(String publicId, String habitId, String date, int count, String status,
                               long revision, int amount, String mood, String note) {
    }

    @Schema(name = "HabitCheckinCommand")
    public record HabitCheckinCommand(String date, Integer count, String status, Integer amount,
                                      String mood, String note) {
    }

    /** 习惯详情面板要的聚合数字：月打卡/总打卡/月完成率/当前连续/月完成量/总完成量 */
    @Schema(name = "HabitStats")
    public record HabitStats(int currentStreak, int longestStreak, int completionRate30Days,
                             int monthCompleted, int monthTarget, List<HabitCheckin> checkins,
                             int totalCompleted, int monthAmount, int totalAmount, int goalDays) {
    }

    @Schema(name = "HabitCalendarDay")
    public record HabitCalendarDay(String date, int completed, int total) {
    }

    @Schema(name = "Countdown")
    public record Countdown(String publicId, String title, String targetDate, String kind, boolean repeatYearly,
                            boolean pinned, String color, String note, long revision) {
    }

    @Schema(name = "CountdownCommand")
    public record CountdownCommand(String title, String targetDate, String kind, Boolean repeatYearly,
                                   Boolean pinned, String color, String note) {
    }
}
