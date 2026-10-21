package com.salarytracker.task;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskEnhancementIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void filtersBoardsSearchesAndAggregatesWithinCurrentUser() {
        Services alice = services(createUser("alice"));
        Services bob = services(createUser("bob"));
        var work = alice.tasks.createList(new TaskModels.TaskListCommand("工作", null, null, 1, false), "work");
        var tag = alice.tasks.createTag(new TaskModels.TaskTagCommand(null, "关键", null, 0));
        alice.tasks.create(new TaskModels.TaskCommand(work.publicId(), "发布增强视图", "包含搜索词", "HIGH", null,
                "2026-10-12T12:00:00Z", false, "Asia/Shanghai", null, null, List.of(tag.publicId()), List.of()), "a-1");
        bob.tasks.create(new TaskModels.TaskCommand(null, "其他用户秘密", "不可见", "HIGH", null, null,
                false, "Asia/Shanghai", null), "b-1");

        var filter = alice.enhancements.createFilter(new TaskEnhancementModels.SmartFilterCommand("高优先级",
                List.of(work.publicId()), List.of(tag.publicId()), List.of("HIGH"), "OPEN", "ANY", "发布",
                true, true, 0, true));
        assertEquals(1, alice.enhancements.filterTasks(filter.publicId(), 0, 20).total());
        assertEquals(1, alice.enhancements.board("priority").columns().stream()
                .filter(column -> column.key().equals("HIGH")).findFirst().orElseThrow().tasks().size());
        assertEquals(4, alice.enhancements.quadrant().columns().size());
        assertEquals(1, alice.enhancements.search("发布").tasks().size());
        assertTrue(alice.enhancements.search("秘密").tasks().isEmpty());
        assertThrows(RuntimeException.class, () -> bob.enhancements.updateFilter(filter.publicId(),
                new TaskEnhancementModels.SmartFilterCommand("越权", List.of(), List.of(), List.of(), "ALL", "ANY", "", true, false, 0, false), 1));
    }

    @Test
    void persistsAndExecutesValidatedAdvancedFilterRules() throws Exception {
        Services service = services(createUser("rules"));
        var inbox = service.tasks.listLists().get(0);
        var high = service.tasks.create(new TaskModels.TaskCommand(inbox.publicId(), "规则命中的任务", "发布说明",
                "HIGH", null, null, false, "Asia/Shanghai", null), "rules-high");
        service.tasks.create(new TaskModels.TaskCommand(inbox.publicId(), "另一项", "其他内容", "LOW",
                null, null, false, "Asia/Shanghai", null), "rules-low");
        var rules = new ObjectMapper().readTree("{\"op\":\"AND\",\"children\":[{" +
                "\"field\":\"priority\",\"operator\":\"EQ\",\"value\":\"HIGH\"},{" +
                "\"op\":\"OR\",\"children\":[{\"field\":\"title\",\"operator\":\"CONTAINS\",\"value\":\"命中\"},{" +
                "\"field\":\"description\",\"operator\":\"CONTAINS\",\"value\":\"无匹配\"}]}]}");
        var filter = service.enhancements.createFilter(new TaskEnhancementModels.SmartFilterCommand("规则",
                List.of(), List.of(), List.of(), "ALL", "ANY", "", true, false, 0, false, "filter", rules));
        assertEquals("filter", filter.icon());
        assertEquals(1, service.enhancements.filterTasks(filter.publicId(), 0, 20).total());
        assertEquals(high.publicId(), service.enhancements.filterTasks(filter.publicId(), 0, 20).items().get(0).publicId());
        assertThrows(IllegalArgumentException.class, () -> service.enhancements.createFilter(
                new TaskEnhancementModels.SmartFilterCommand("非法规则", List.of(), List.of(), List.of(), "ALL", "ANY", "",
                        true, false, 1, false, null, new ObjectMapper().createObjectNode().put("field", "userSql"))));
        var foreignListRule = new ObjectMapper().readTree("{\"op\":\"AND\",\"children\":[{\"field\":\"listId\",\"operator\":\"EQ\",\"value\":\""
                + UUID.randomUUID() + "\"}]}");
        assertThrows(IllegalArgumentException.class, () -> service.enhancements.createFilter(
                new TaskEnhancementModels.SmartFilterCommand("越权清单", List.of(), List.of(), List.of(), "ALL", "ANY", "",
                        true, false, 1, false, null, foreignListRule)));
    }

    @Test
    void advancedTagAndLogicMatchesEverySelectedTag() throws Exception {
        Services service = services(createUser("tag-and"));
        var inbox = service.tasks.listLists().get(0);
        var first = service.tasks.createTag(new TaskModels.TaskTagCommand(null, "标签一", null, 0));
        var second = service.tasks.createTag(new TaskModels.TaskTagCommand(null, "标签二", null, 1));
        var both = service.tasks.create(new TaskModels.TaskCommand(inbox.publicId(), "同时含两个标签", null, "NONE",
                null, null, false, "Asia/Shanghai", null, null, List.of(first.publicId(), second.publicId()), List.of()), "tag-and-both");
        service.tasks.create(new TaskModels.TaskCommand(inbox.publicId(), "只含第一个标签", null, "NONE",
                null, null, false, "Asia/Shanghai", null, null, List.of(first.publicId()), List.of()), "tag-and-first");
        var rules = new ObjectMapper().readTree("{\"op\":\"AND\",\"children\":[" +
                "{\"field\":\"tagId\",\"operator\":\"EQ\",\"value\":\"" + first.publicId() + "\"}," +
                "{\"field\":\"tagId\",\"operator\":\"EQ\",\"value\":\"" + second.publicId() + "\"}]}");
        var filter = service.enhancements.createFilter(new TaskEnhancementModels.SmartFilterCommand("标签且",
                List.of(), List.of(), List.of(), "ALL", "ANY", "", true, false, 0, false, null, rules));
        var result = service.enhancements.filterTasks(filter.publicId(), 0, 20);
        assertEquals(1, result.total());
        assertEquals(both.publicId(), result.items().get(0).publicId());
    }

    @Test
    void advancedDateExclusionIncludesTasksWithoutDueDates() throws Exception {
        Services service = services(createUser("date-not"));
        var inbox = service.tasks.listLists().get(0);
        var inside = service.tasks.create(new TaskModels.TaskCommand(inbox.publicId(), "区间内", null, "NONE",
                null, "2026-10-22T12:00:00Z", false, "Asia/Shanghai", null), "date-not-inside");
        var outside = service.tasks.create(new TaskModels.TaskCommand(inbox.publicId(), "区间外", null, "NONE",
                null, "2026-10-28T12:00:00Z", false, "Asia/Shanghai", null), "date-not-outside");
        var noDate = service.tasks.create(new TaskModels.TaskCommand(inbox.publicId(), "无日期", null, "NONE",
                null, null, false, "Asia/Shanghai", null), "date-not-empty");
        var rules = new ObjectMapper().readTree("{\"op\":\"OR\",\"children\":[" +
                "{\"field\":\"hasDueDate\",\"operator\":\"EQ\",\"value\":false}," +
                "{\"field\":\"dueAt\",\"operator\":\"LT\",\"value\":\"2026-10-21\"}," +
                "{\"field\":\"dueAt\",\"operator\":\"GT\",\"value\":\"2026-10-25\"}]} ");
        var filter = service.enhancements.createFilter(new TaskEnhancementModels.SmartFilterCommand("日期排除",
                List.of(), List.of(), List.of(), "ALL", "ANY", "", true, false, 0, false, null, rules));

        var result = service.enhancements.filterTasks(filter.publicId(), 0, 20);
        assertEquals(2, result.total());
        assertTrue(result.items().stream().anyMatch(task -> task.publicId().equals(outside.publicId())));
        assertTrue(result.items().stream().anyMatch(task -> task.publicId().equals(noDate.publicId())));
        assertFalse(result.items().stream().anyMatch(task -> task.publicId().equals(inside.publicId())));
    }

    @Test
    void persistsAndExecutesBasicFilterModesDateRangesTagsAndObjectTypes() {
        Services service = services(createUser("basic-filter"));
        var noteList = service.tasks.createList(new TaskModels.TaskListCommand("笔记", null, null, 1,
                false, false, "LIST", "NOTE"), "basic-filter-note-list");
        var taskList = service.tasks.createList(new TaskModels.TaskListCommand("任务", null, null, 2,
                false, false, "LIST", "TASK"), "basic-filter-task-list");
        var tagged = service.tasks.createTag(new TaskModels.TaskTagCommand(null, "有标签", null, 0));
        var due = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(3)
                .atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toString();
        var note = service.tasks.create(new TaskModels.TaskCommand(noteList.publicId(), "计划笔记", null, "HIGH",
                null, due, false, "Asia/Shanghai", null, null, List.of(tagged.publicId()), List.of()), "basic-note");
        service.tasks.create(new TaskModels.TaskCommand(taskList.publicId(), "普通任务", null, "HIGH",
                null, due, false, "Asia/Shanghai", null), "basic-task");

        var filter = service.enhancements.createFilter(new TaskEnhancementModels.SmartFilterCommand("笔记筛选",
                "BASIC", List.of(), List.of(tagged.publicId()), "SELECTED", List.of("NOTE"), List.of("HIGH"),
                "OPEN", "CUSTOM", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(2).toString(),
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(5).toString(), "计划", true, false,
                0, false, "book", null));
        assertEquals("BASIC", filter.mode());
        assertEquals(List.of("NOTE"), filter.taskTypes());
        assertEquals(note.publicId(), service.enhancements.filterTasks(filter.publicId(), 0, 20).items().get(0).publicId());

        var excludedList = service.tasks.createList(new TaskModels.TaskListCommand("不进智能清单", null, null, 3,
                false, false, "LIST", "TASK", false), "basic-filter-excluded-list");
        service.tasks.create(new TaskModels.TaskCommand(excludedList.publicId(), "排除任务", null, "HIGH",
                null, due, false, "Asia/Shanghai", null), "basic-filter-excluded-task");
        var allTasks = service.enhancements.createFilter(new TaskEnhancementModels.SmartFilterCommand("所有任务",
                "BASIC", List.of(), List.of(), "ALL", List.of("TASK"), List.of(), "ALL", "ANY", null, null,
                "", true, false, 4, false, null, null));
        assertEquals(1, service.enhancements.filterTasks(allTasks.publicId(), 0, 20).total());

        var noTags = service.enhancements.createFilter(new TaskEnhancementModels.SmartFilterCommand("無标签",
                "BASIC", List.of(taskList.publicId()), List.of(), "NO_TAGS", List.of("TASK"), List.of(),
                "ALL", "ANY", null, null, "", true, false, 1, false, null, null));
        assertEquals(1, service.enhancements.filterTasks(noTags.publicId(), 0, 20).total());
        assertThrows(IllegalArgumentException.class, () -> service.enhancements.createFilter(
                new TaskEnhancementModels.SmartFilterCommand("无效日期", "BASIC", List.of(), List.of(), "SELECTED",
                        List.of(), List.of(), "ALL", "CUSTOM", "2026-10-22", "2026-10-22", "", true, false,
                        2, false, null, null)));
    }

    @Test
    void previewsCsvWithoutWritesAndCommitsOnlyOnce() throws Exception {
        Services service = services(createUser("import"));
        long before = service.tasks.listTasks(null, null, "ALL", null, 0, 20).total();
        String csv = "标题,描述,清单,到期日期,优先级,标签\n=危险公式,测试,导入清单,2026年10月12日,高,工作|重要\n,缺标题,导入清单,2026-10-12,低,\n";
        var file = new MockMultipartFile("file", "tasks.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var preview = service.enhancements.preview(file);
        assertEquals(1, preview.valid());
        assertEquals(1, preview.errors());
        assertEquals(before, service.tasks.listTasks(null, null, "ALL", null, 0, 20).total());
        var result = service.enhancements.commit(new TaskEnhancementModels.ImportCommitCommand(preview.previewId(), true));
        assertEquals(1, result.created());
        assertTrue(service.enhancements.commit(new TaskEnhancementModels.ImportCommitCommand(preview.previewId(), true)).replayed());
        assertEquals(before + 1, service.tasks.listTasks(null, null, "ALL", null, 0, 20).total());
        String exported = new String(service.enhancements.export(new TaskEnhancementModels.ExportCommand("csv", true)), StandardCharsets.UTF_8);
        assertTrue(exported.contains("'=危险公式"));
        assertFalse(exported.contains("\n=危险公式"));
    }

    @Test
    void parsesQuickAddAndPreviewsRealExcelContent() throws Exception {
        Services service = services(createUser("excel"));
        var parsed = service.enhancements.parse(new TaskEnhancementModels.ParseCommand(
                "明天 发布版本 !!! #发布 @工作 每周"));
        assertEquals("发布版本", parsed.title());
        assertEquals("HIGH", parsed.priority());
        assertEquals(List.of("发布"), parsed.tagNames());
        assertEquals("工作", parsed.listName());
        assertEquals("FREQ=WEEKLY", parsed.rrule());
        assertTrue(parsed.dueAt() != null && !parsed.dueAt().isBlank());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output)
                .head(List.of(List.of("任务ID"), List.of("类型"), List.of("标题"), List.of("清单ID"),
                        List.of("清单名称"), List.of("状态"), List.of("优先级"), List.of("开始时间"),
                        List.of("截止时间"), List.of("标签"), List.of("进度"), List.of("创建时间"),
                        List.of("修改时间"), List.of("完成时间"), List.of("备注内容")))
                .sheet("任务")
                .doWrite(List.of(List.of("source-1", "任务", "Excel 导入任务", "list-1", "工作",
                        "已完成", "高", "2026-10-12 08:00:00", "2026-10-12 18:30:00", "发布",
                        100, "2026-10-01 09:00:00", "2026-10-12 19:00:00",
                        "2026-10-12 19:00:00", "真实导出结构备注")));
        var file = new MockMultipartFile("file", "tasks.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        var preview = service.enhancements.preview(file);
        assertEquals(1, preview.valid());
        assertEquals(0, preview.errors());
        assertEquals("任务", preview.rows().get(0).sheet());
        assertEquals("工作", preview.rows().get(0).listName());
        assertEquals("真实导出结构备注", preview.rows().get(0).description());
        assertEquals("COMPLETED", preview.rows().get(0).status());
        assertEquals("HIGH", preview.rows().get(0).priority());
        assertTrue(preview.rows().get(0).dueAt() != null && !preview.rows().get(0).dueAt().isBlank());
    }

    private Services services(long userId) {
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(userId);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TaskService tasks = new TaskService(jdbc, resolver, new TaskChangeLog(jdbc, mapper));
        return new Services(tasks, new TaskEnhancementService(jdbc, resolver, tasks, mapper));
    }

    private long createUser(String suffix) {
        String username = "task-enhanced-" + suffix + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '增强测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private record Services(TaskService tasks, TaskEnhancementService enhancements) { }
}
