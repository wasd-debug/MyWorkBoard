package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.task.TaskListsListTool;
import com.salarytracker.ai.tool.task.TaskTagsListTool;
import com.salarytracker.ai.tool.task.TaskToolsConfiguration;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 锁定 task.today / task.overdue 的日界与逾期口径：今天只含当天到期，
 * 逾期只含到期日早于今天，当天到期即使时刻已过也不算逾期。
 */
class TaskDueWindowToolsIntegrationTest extends MySqlIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void todayAndOverdueSplitByCalendarDayInsteadOfInstant() {
        long userId = createUser();
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(userId);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TaskService tasks = new TaskService(jdbc, resolver,
                new TaskChangeLog(jdbc, mapper));
        TaskToolsConfiguration tools = new TaskToolsConfiguration();

        LocalDate today = LocalDate.now(ZONE);
        create(tasks, "昨天到期", isoAt(today.minusDays(1), 10));
        create(tasks, "今天上午已过", isoAt(today, 1));
        create(tasks, "今天深夜", isoAt(today, 23));
        create(tasks, "明天到期", isoAt(today.plusDays(1), 9));
        create(tasks, "没有到期日", null);
        var completed = create(tasks, "今天已完成", isoAt(today, 8));
        tasks.complete(completed.publicId(), String.valueOf(completed.revision()));

        var todayResult = tools.taskTodayTool(tasks, mapper).execute(mapper.createObjectNode());
        List<String> todayTitles = titles(todayResult.structuredContent());
        assertEquals(List.of("今天上午已过", "今天深夜"), todayTitles,
                "今天只含当天到期且未完成的任务，按到期时间升序");

        var overdueResult = tools.taskOverdueTool(tasks, mapper).execute(mapper.createObjectNode());
        assertEquals(List.of("昨天到期"), titles(overdueResult.structuredContent()),
                "当天到期即使时刻已过也不算逾期");
    }

    @Test
    void catalogToolsExposeRealListAndTagIdsAndSkipArchivedLists() {
        long userId = createUser();
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(userId);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TaskService tasks = new TaskService(jdbc, resolver,
                new TaskChangeLog(jdbc, mapper));

        var work = tasks.createList(new TaskModels.TaskListCommand("工作", "#2563eb", null, 1, false), "catalog-work");
        var archived = tasks.createList(new TaskModels.TaskListCommand("归档清单", null, null, 2, false), "catalog-archived");
        tasks.updateList(archived.publicId(), new TaskModels.TaskListCommand(null, null, null, null, true),
                String.valueOf(archived.revision()));
        var tag = tasks.createTag(new TaskModels.TaskTagCommand(null, "项目A", "#16a34a", 0));

        var lists = new TaskListsListTool(tasks, mapper).execute(mapper.createObjectNode());
        String listsJson = lists.structuredContent().toString();
        assertTrue(listsJson.contains(work.publicId()), "必须返回真实清单 ID");
        assertFalse(listsJson.contains(archived.publicId()), "默认排除已归档清单");
        assertTrue(listsJson.contains("groups"), "清单分组与清单一起返回");

        var allLists = new TaskListsListTool(tasks, mapper)
                .execute(mapper.createObjectNode().put("includeArchived", true));
        assertTrue(allLists.structuredContent().toString().contains(archived.publicId()));

        var tags = new TaskTagsListTool(tasks, mapper).execute(mapper.createObjectNode());
        assertTrue(tags.structuredContent().toString().contains(tag.publicId()), "必须返回真实标签 ID");
    }

    private TaskModels.TaskItem create(TaskService tasks, String title, String dueAt) {
        return tasks.create(new TaskModels.TaskCommand(null, title, "", "NONE", null, dueAt, false,
                "Asia/Shanghai", null, null, List.of(), List.of()), "due-" + UUID.randomUUID());
    }

    private static String isoAt(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZONE).toInstant().toString();
    }

    private static List<String> titles(com.fasterxml.jackson.databind.JsonNode content) {
        var list = new java.util.ArrayList<String>();
        content.forEach(item -> list.add(item.path("title").asText()));
        return list;
    }

    private long createUser() {
        String username = "task-due-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '日界测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
