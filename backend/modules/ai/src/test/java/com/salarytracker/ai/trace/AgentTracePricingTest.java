package com.salarytracker.ai.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTracePricingTest {
    @Test
    void recognizesDeepSeekPeakWindowsInChinaTime() {
        assertTrue(AgentTraceService.deepSeekPeak(Instant.parse("2026-09-23T01:00:00Z")));
        assertTrue(AgentTraceService.deepSeekPeak(Instant.parse("2026-09-23T06:00:00Z")));
        assertFalse(AgentTraceService.deepSeekPeak(Instant.parse("2026-09-23T04:30:00Z")));
        assertFalse(AgentTraceService.deepSeekPeak(Instant.parse("2026-09-23T10:00:00Z")));
        assertFalse(AgentTraceService.deepSeekPeak(Instant.parse("2026-09-26T02:00:00Z")));
    }
}
