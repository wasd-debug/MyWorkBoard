package com.salarytracker.calendar;

import com.salarytracker.task.TaskModels;
import com.salarytracker.task.TaskEfficiencyModels;
import com.salarytracker.worktime.WorktimeModels;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public final class TaskCalendarModels {
    private TaskCalendarModels() {
    }

    @Schema(name = "TaskCalendarLayerStatus")
    public record LayerStatus(boolean available, String message) {
        public static LayerStatus ok() { return new LayerStatus(true, null); }
        public static LayerStatus failed() { return new LayerStatus(false, "暂不可用"); }
    }

    @Schema(name = "TaskCalendarLedgerDay")
    public record LedgerDay(String date, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }

    @Schema(name = "TaskCalendarHolidayDay")
    public record HolidayDay(String date, String name, boolean off) {
    }

    @Schema(name = "TaskCalendarLunarDay")
    public record LunarDay(String date, String lunarDate, String festival, String solarTerm) {
    }

    @Schema(name = "TaskCalendarResponse")
    public record CalendarResponse(String from, String to, List<TaskModels.CalendarItem> tasks,
                                   List<WorktimeModels.CalendarItem> worktime, List<LedgerDay> ledger,
                                   List<HolidayDay> holidays, List<TaskEfficiencyModels.HabitCalendarDay> habits,
                                   List<LunarDay> lunar,
                                   java.util.Map<String, LayerStatus> layers) {
    }
}
