package com.salarytracker.worktime;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorktimeCalculatorTest {
    @Test
    void deductsConfiguredLunchAndRecordRestFromActualTime() {
        WorktimeCalculator.Result result = WorktimeCalculator.calculate(
                "09:00", "18:30", 90, 0,
                "09:00", "18:00",
                new BigDecimal("8700"), new BigDecimal("21.75"), false);

        assertEquals(480, result.actualMin());
        assertEquals(30, result.overtimeMin());
        assertEquals(new BigDecimal("50.00"), result.realHourlyWage());
    }

    @Test
    void supportsOvernightSchedulesAndAdditionalRest() {
        WorktimeCalculator.Result result = WorktimeCalculator.calculate(
                "22:00", "07:30", 60, 30,
                "22:00", "06:00",
                new BigDecimal("8400"), new BigDecimal("21"), false);

        assertEquals(480, result.actualMin());
        assertEquals(60, result.overtimeMin());
        assertEquals(new BigDecimal("50.00"), result.realHourlyWage());
    }

    @Test
    void unfinishedRecordHasNoPersistedOvertimeOrHourlyWage() {
        WorktimeCalculator.Result result = WorktimeCalculator.calculate(
                "09:00", "", 90, 0,
                "09:00", "18:00",
                new BigDecimal("8700"), new BigDecimal("21.75"), false);

        assertEquals(0, result.actualMin());
        assertEquals(0, result.overtimeMin());
        assertEquals(new BigDecimal("0"), result.realHourlyWage());
    }

    @Test
    void countsAllNetWorkAsOvertimeOnAnOffDay() {
        WorktimeCalculator.Result result = WorktimeCalculator.calculate(
                "08:30", "22:00", 120, 0,
                "08:30", "17:30",
                new BigDecimal("8700"), new BigDecimal("21.75"), true);

        assertEquals(690, result.actualMin());
        assertEquals(690, result.overtimeMin());
    }

    @Test
    void neverPersistsNegativeOvertimeForShortWorkdays() {
        WorktimeCalculator.Result result = WorktimeCalculator.calculate(
                "09:00", "18:00", 90, 90,
                "09:00", "18:00",
                new BigDecimal("8700"), new BigDecimal("21.75"), false);

        assertEquals(360, result.actualMin());
        assertEquals(0, result.overtimeMin());
    }
}
