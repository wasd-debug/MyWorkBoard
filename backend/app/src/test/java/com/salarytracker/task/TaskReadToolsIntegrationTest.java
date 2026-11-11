package com.salarytracker.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.ai.tool.task.TaskToolsConfiguration;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P1 只读工具：task.query 的关键词/回收站/多清单、task.get 的详情、task.calendar 的区间上限，
 * 以及习惯/专注/倒数日工具的参数透传。
 */
class TaskReadToolsIntegrationTest extends MySqlIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TaskToolsConfiguration tools = new TaskToolsConfiguration();

    @Test
    void queryFiltersByKeywordAndSplitsTrashAndAbandoned() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        var open = create(tasks, "写周报");
        create(tasks, "买牛奶");
        var removed = create(tasks, "写季度总结");
        tasks.delete(removed.publicId(), String.valueOf(removed.revision()));

        var byKeyword = tools.taskQueryTool(tasks, mapper)
                .execute(mapper.createObjectNode().put("keyword", "周报"));
        assertEquals(List.of("写周报"), titles(byKeyword.structuredContent()));
        assertEquals(0, byKeyword.structuredContent().path("page").asInt(), "分页信息随结果返回");

        var withDeleted = tools.taskQueryTool(tasks, mapper)
                .execute(mapper.createObjectNode().put("keyword", "写"));
        assertEquals(List.of("写周报"), titles(withDeleted.structuredContent()), "默认不返回回收站任务");

        var trash = tools.taskQueryTool(tasks, mapper)
                .execute(mapper.createObjectNode().put("view", "TRASH"));
        assertEquals(List.of("写季度总结"), titles(trash.structuredContent()), "view=TRASH 只查回收站");

        assertTrue(open.publicId() != null);
    }

    @Test
    void queryMergesMultipleListsAndKeepsSizeLimit() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        var work = tasks.createList(new TaskModels.TaskListCommand("工作", "#2563eb", null, 1, false), "read-work");
        var home = tasks.createList(new TaskModels.TaskListCommand("生活", null, null, 2, false), "read-home");
        createIn(tasks, work.publicId(), "工作项 A");
        createIn(tasks, work.publicId(), "工作项 B");
        createIn(tasks, home.publicId(), "生活项");

        var mergedInput = mapper.createObjectNode();
        mergedInput.set("listIds", mapper.createArrayNode().add(work.publicId()).add(home.publicId()));
        var merged = tools.taskQueryTool(tasks, mapper).execute(mergedInput);
        List<String> mergedTitles = titles(merged.structuredContent());
        assertEquals(3, mergedTitles.size());
        assertTrue(mergedTitles.containsAll(List.of("工作项 A", "工作项 B", "生活项")));
        assertEquals(3, merged.structuredContent().path("total").asInt());

        var limitedInput = mapper.createObjectNode();
        limitedInput.set("listIds", mapper.createArrayNode().add(work.publicId()).add(home.publicId()));
        limitedInput.put("size", 2);
        var limited = tools.taskQueryTool(tasks, mapper).execute(limitedInput);
        assertEquals(2, titles(limited.structuredContent()).size(), "size 截断合并结果");
    }

    @Test
    void getReturnsTaskWithListName() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        var work = tasks.createList(new TaskModels.TaskListCommand("工作", "#2563eb", null, 1, false), "read-get");
        var task = createIn(tasks, work.publicId(), "交周报");

        var result = tools.taskGetTool(tasks, mapper).execute(mapper.createObjectNode().put("taskId", task.publicId()));
        assertEquals(ToolStatus.COMPLETED, result.status());
        assertEquals("交周报", result.structuredContent().path("task").path("title").asText());
        assertEquals("工作", result.structuredContent().path("listName").asText());
    }

    @Test
    void calendarRejectsRangeOverNinetyTwoDays() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        LocalDate today = LocalDate.now(ZONE);
        var tool = tools.taskCalendarTool(tasks, mapper);

        var ok = tool.execute(mapper.createObjectNode().put("from", today.toString())
                .put("to", today.plusDays(7).toString()));
        assertEquals(ToolStatus.COMPLETED, ok.status());

        assertThrows(IllegalArgumentException.class, () -> tool.execute(mapper.createObjectNode()
                .put("from", today.toString()).put("to", today.plusDays(93).toString())));
    }

    @Test
    void habitFocusAndCountdownToolsPassParametersThrough() {
        TaskEfficiencyService efficiency = mock(TaskEfficiencyService.class);
        when(efficiency.habits(true)).thenReturn(List.of());
        when(efficiency.habitCalendar(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3))).thenReturn(List.of());
        when(efficiency.countdowns()).thenReturn(List.of());
        when(efficiency.countdownGroups()).thenReturn(List.of());

        assertEquals(ToolStatus.COMPLETED, tools.habitListTool(efficiency, mapper)
                .execute(mapper.createObjectNode().put("includeArchived", true)).status());
        assertEquals(ToolStatus.COMPLETED, tools.habitCalendarTool(efficiency, mapper)
                .execute(mapper.createObjectNode().put("from", "2026-11-01").put("to", "2026-11-02")).status());
        var countdowns = tools.countdownListTool(efficiency, mapper).execute(mapper.createObjectNode());
        assertTrue(countdowns.structuredContent().has("countdowns"));
        assertTrue(countdowns.structuredContent().has("groups"));
        assertEquals(ToolStatus.COMPLETED, tools.focusCurrentTool(efficiency, mapper)
                .execute(mapper.createObjectNode()).status());

        assertThrows(IllegalArgumentException.class, () -> tools.habitCalendarTool(efficiency, mapper)
                .execute(mapper.createObjectNode().put("from", "2026-01-01").put("to", "2026-12-31")),
                "习惯日历同样限制 92 天");
    }

    private TaskService taskService(long userId) {
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(userId);
        return new TaskService(jdbc, resolver, new TaskChangeLog(jdbc, mapper));
    }

    /**
     * 只读工具的 summary 必须是结果摘要，不能回工具描述原文——否则模型和 MCP 客户端会把
     * 「查询当前用户今天到期且未完成的任务…」当成查询结果，既浪费上下文又容易误判。
     */
    @Test
    void readToolsSummarizeResultCountsInsteadOfToolDescription() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        var dueAt = LocalDate.now(ZONE).atTime(10, 0).atZone(ZONE).toInstant().toString();
        tasks.create(new TaskModels.TaskCommand(null, "今天的事", "", "NONE", null, dueAt, false,
                "Asia/Shanghai", null, null, List.of(), List.of()), "read-" + UUID.randomUUID());

        var today = tools.taskTodayTool(tasks, mapper).execute(mapper.createObjectNode());
        assertEquals(ToolStatus.COMPLETED, today.status());
        assertEquals("今天的任务 1 条", today.summary());

        var empty = tools.taskTodayTool(taskService(createUser()), mapper).execute(mapper.createObjectNode());
        assertEquals("没有今天的任务", empty.summary());
    }

    private TaskModels.TaskItem create(TaskService tasks, String title) {
        return tasks.create(new TaskModels.TaskCommand(null, title, "", "NONE", null, null, false,
                "Asia/Shanghai", null, null, List.of(), List.of()), "read-" + UUID.randomUUID());
    }

    private TaskModels.TaskItem createIn(TaskService tasks, String listId, String title) {
        return tasks.create(new TaskModels.TaskCommand(listId, title, "", "NONE", null, null, false,
                "Asia/Shanghai", null, null, List.of(), List.of()), "read-" + UUID.randomUUID());
    }

    private static List<String> titles(JsonNode content) {
        JsonNode items = content.path("items");
        List<String> result = new ArrayList<>();
        items.forEach(item -> result.add(item.path("title").asText()));
        return result;
    }

    private long createUser() {
        String username = "task-read-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '只读工具测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
