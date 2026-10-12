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
