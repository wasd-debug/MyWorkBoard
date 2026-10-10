package com.salarytracker.calendar;

import com.salarytracker.ledger.LedgerModels;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.service.HolidayService;
import com.salarytracker.task.TaskModels;
import com.salarytracker.task.TaskService;
import com.salarytracker.task.TaskEfficiencyService;
import com.salarytracker.task.TaskEfficiencyModels;
import com.salarytracker.worktime.WorktimeModels;
import com.salarytracker.worktime.WorktimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.salarytracker.calendar.TaskCalendarModels.*;

@Service
public class TaskCalendarService {
    private static final Logger log = LoggerFactory.getLogger(TaskCalendarService.class);
    private static final Set<String> SUPPORTED_LAYERS = Set.of("worktime", "ledger", "holiday", "habit");

    private final TaskService tasks;
    private final WorktimeService worktime;
    private final LedgerTransactionService ledger;
    private final HolidayService holidays;
    private final LunarCalendar lunarCalendar;
    private final TaskEfficiencyService efficiency;
    private final boolean lunarEnabled;

    public TaskCalendarService(TaskService tasks, WorktimeService worktime, LedgerTransactionService ledger,
                               HolidayService holidays, LunarCalendar lunarCalendar, TaskEfficiencyService efficiency,
                               @Value("${task.lunar-enabled:true}") boolean lunarEnabled) {
        this.tasks = tasks;
        this.worktime = worktime;
        this.ledger = ledger;
        this.holidays = holidays;
        this.lunarCalendar = lunarCalendar;
        this.efficiency = efficiency;
        this.lunarEnabled = lunarEnabled;
    }

    public CalendarResponse calendar(LocalDate from, LocalDate toExclusive, String requestedLayers, boolean lunar) {
        validateRange(from, toExclusive);
        LocalDate inclusiveEnd = toExclusive.minusDays(1);
        Set<String> selected = layers(requestedLayers);
        Map<String, LayerStatus> statuses = new LinkedHashMap<>();

        ZoneId taskZone = ZoneId.of(tasks.settings().timezone());
        List<TaskModels.CalendarItem> taskItems = tasks.calendarItems(
                from.atStartOfDay(taskZone).toInstant(), toExclusive.atStartOfDay(taskZone).toInstant());
        List<WorktimeModels.CalendarItem> worktimeItems = selected.contains("worktime")
                ? layer("worktime", statuses, () -> worktime.calendarItems(from, inclusiveEnd)) : List.of();
        List<LedgerDay> ledgerItems = selected.contains("ledger")
                ? layer("ledger", statuses, () -> ledger.calendarDailyTotals(from, inclusiveEnd).stream()
                    .map(item -> new LedgerDay(item.date().toString(), item.income(), item.expense(),
                            item.income().subtract(item.expense()))).toList()) : List.of();
        List<HolidayDay> holidayItems = selected.contains("holiday")
                ? layer("holiday", statuses, () -> holidayDays(from, inclusiveEnd)) : List.of();
        List<TaskEfficiencyModels.HabitCalendarDay> habitItems = selected.contains("habit")
                ? layer("habit", statuses, () -> efficiency.habitCalendar(from, toExclusive)) : List.of();
        List<LunarDay> lunarItems = lunar && lunarEnabled ? lunarDays(from, inclusiveEnd) : List.of();
        return new CalendarResponse(from.toString(), toExclusive.toString(), taskItems, worktimeItems, ledgerItems,
                holidayItems, habitItems, lunarItems, statuses);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from)) throw new IllegalArgumentException("日历时间范围无效");
        if (ChronoUnit.DAYS.between(from, to) > 370) throw new IllegalArgumentException("日历查询范围不能超过 370 天");
    }

    private Set<String> layers(String raw) {
        if (raw == null) return new LinkedHashSet<>(SUPPORTED_LAYERS);
        if (raw.isBlank()) return new LinkedHashSet<>();
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw.split(",")) {
            String layer = value.trim().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_LAYERS.contains(layer)) throw new IllegalArgumentException("不支持的日历叠加层: " + layer);
            result.add(layer);
        }
        return result;
    }

    private List<HolidayDay> holidayDays(LocalDate from, LocalDate to) {
        List<HolidayDay> result = new ArrayList<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            HolidayService.HolidayResponse response = holidays.getHolidays(year);
            response.days().forEach((date, value) -> {
                LocalDate current = LocalDate.parse(date);
                if (!current.isBefore(from) && !current.isAfter(to)) result.add(new HolidayDay(date, value.name(), value.off()));
            });
        }
        return result.stream().sorted(java.util.Comparator.comparing(HolidayDay::date)).toList();
    }

    private List<LunarDay> lunarDays(LocalDate from, LocalDate to) {
        List<LunarDay> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            try {
                LunarCalendar.LunarDay value = lunarCalendar.day(date);
                if (value != null) result.add(new LunarDay(date.toString(), value.lunarDate(), value.festival(), value.solarTerm()));
            } catch (RuntimeException exception) {
                log.warn("农历计算降级 date={}: {}", date, exception.getMessage());
            }
        }
        return result;
    }

    private <T> List<T> layer(String name, Map<String, LayerStatus> statuses, LayerSupplier<T> supplier) {
        try {
            List<T> result = supplier.get();
            statuses.put(name, LayerStatus.ok());
            return result;
        } catch (RuntimeException exception) {
            log.warn("任务日历叠加层降级 layer={}: {}", name, exception.getMessage());
            statuses.put(name, LayerStatus.failed());
            return List.of();
        }
    }

    @FunctionalInterface
    private interface LayerSupplier<T> {
        List<T> get();
    }
}
