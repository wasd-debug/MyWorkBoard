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
                               String status, String deviceLabel, long revision) {
    }

    @Schema(name = "FocusStartCommand")
    public record FocusStartCommand(String taskId, Integer plannedMinutes, String deviceLabel, String requestedDate) {
    }

    @Schema(name = "FocusFinishCommand")
    public record FocusFinishCommand(String status) {
    }

    @Schema(name = "FocusStats")
    public record FocusStats(long todayCount, long todayMinutes, long weekCount, long weekMinutes,
                             long monthCount, long monthMinutes, List<FocusDaily> recentDays) {
    }

    public record FocusDaily(String date, long count, long minutes) {
    }

    @Schema(name = "Habit")
    public record Habit(String publicId, String name, String icon, String color, String frequency,
                        int targetCount, List<Integer> customDays, String remindAt, String startDate,
                        boolean archived, int sortOrder, long revision) {
    }

    @Schema(name = "HabitCommand")
    public record HabitCommand(String name, String icon, String color, String frequency, Integer targetCount,
                               List<Integer> customDays, String remindAt, String startDate, Boolean archived,
                               Integer sortOrder) {
    }

    @Schema(name = "HabitCheckin")
    public record HabitCheckin(String publicId, String habitId, String date, int count, String status,
                               long revision) {
    }

    @Schema(name = "HabitCheckinCommand")
    public record HabitCheckinCommand(String date, Integer count, String status) {
    }

    @Schema(name = "HabitStats")
    public record HabitStats(int currentStreak, int longestStreak, int completionRate30Days,
                             int monthCompleted, int monthTarget, List<HabitCheckin> checkins) {
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
