package com.salarytracker.ai.tool.worktime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.worktime.WorktimeModels.Basis;
import com.salarytracker.worktime.WorktimeModels.Settings;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;
import com.salarytracker.worktime.WorktimeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorktimeQueryToolsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void settingsToolReturnsServiceFact() {
        WorktimeService service = mock(WorktimeService.class);
        Settings settings = new Settings(new BigDecimal("10000"), new BigDecimal("8000"), Basis.PRE,
                "09:00", "18:00", 60, new BigDecimal("21.75"), true, Map.of(), 3L);
        when(service.readSettings()).thenReturn(settings);

        var result = new WorktimeSettingsGetTool(service, mapper).execute(mapper.createObjectNode());

        assertEquals(ToolStatus.COMPLETED, result.status());
        assertEquals(3L, result.structuredContent().path("revision").asLong());
    }

    @Test
    void recordSearchAppliesBoundedPaginationAndDates() {
        WorktimeService service = mock(WorktimeService.class);
        WorkRecord record = new WorkRecord(5L, "2026-09-20", "09:00", "20:30", 60, 150,
                new BigDecimal("40.50"), "release", "v1", "Asia/Shanghai", 2L);
        when(service.listRecords("2026-09-01", "2026-09-20", 20, 10)).thenReturn(List.of(record));
        var input = mapper.createObjectNode()
                .put("from", "2026-09-01").put("to", "2026-09-20")
                .put("limit", 20).put("offset", 10);

        var result = new WorktimeRecordsSearchTool(service, mapper).execute(input);

        assertEquals(1, result.structuredContent().path("returned").asInt());
        assertEquals(5L, result.structuredContent().path("items").path(0).path("id").asLong());
        verify(service).listRecords("2026-09-01", "2026-09-20", 20, 10);
    }

    @Test
    void recordSearchRejectsInvalidRangeAndOversizedPage() {
        WorktimeRecordsSearchTool tool = new WorktimeRecordsSearchTool(mock(WorktimeService.class), mapper);

        assertThrows(IllegalArgumentException.class, () -> tool.execute(mapper.createObjectNode()
                .put("from", "2026-09-20").put("to", "2026-09-01")));
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(mapper.createObjectNode().put("limit", 101)));
    }
}
