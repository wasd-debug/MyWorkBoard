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
                new BigDecimal("8700"), new BigDecimal("21.75"));

        assertEquals(480, result.actualMin());
        assertEquals(30, result.overtimeMin());
        assertEquals(new BigDecimal("50.00"), result.realHourlyWage());
    }

    @Test
    void supportsOvernightSchedulesAndAdditionalRest() {
        WorktimeCalculator.Result result = WorktimeCalculator.calculate(
                "22:00", "07:30", 60, 30,
                "22:00", "06:00",
                new BigDecimal("8400"), new BigDecimal("21"));

        assertEquals(480, result.actualMin());
        assertEquals(60, result.overtimeMin());
        assertEquals(new BigDecimal("50.00"), result.realHourlyWage());
    }

    @Test
    void unfinishedRecordHasNoPersistedOvertimeOrHourlyWage() {
        WorktimeCalculator.Result result = WorktimeCalculator.calculate(
                "09:00", "", 90, 0,
                "09:00", "18:00",
                new BigDecimal("8700"), new BigDecimal("21.75"));

        assertEquals(0, result.actualMin());
        assertEquals(0, result.overtimeMin());
        assertEquals(new BigDecimal("0"), result.realHourlyWage());
    }
}
