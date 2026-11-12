package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.task.TaskModels.ReminderCommand;
import com.salarytracker.task.TaskModels.TaskCommand;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRecurrenceReminderIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void completionKeepsHistoryCreatesOneNextInstanceAndCancelsPendingReminder() {
        long userId = createUser();
        CurrentUserResolver current = current(userId);
        TaskService tasks = new TaskService(jdbc, current, new TaskChangeLog(jdbc,
                new ObjectMapper().findAndRegisterModules()));
        TaskReminderService reminders = new TaskReminderService(jdbc, current, new TaskInboxStream());
        var task = tasks.create(new TaskCommand(null, "每日复盘", "", "NONE", null,
                Instant.now().minusSeconds(60).toString(), false, "UTC", null, null, null, null,
                "FREQ=DAILY;COUNT=3", "DUE_DATE"), "repeat-create");
        var reminder = reminders.create(task.publicId(), new ReminderCommand("RELATIVE", 30, null, "IN_APP", false));

        tasks.scanDueRecurrences();
        tasks.scanDueRecurrences();
        var completed = tasks.complete(task.publicId(), "1");
        assertEquals("COMPLETED", completed.status());
        var all = tasks.listTasks(null, null, "ALL", null, 0, 20);
        assertEquals(2, all.total());
        var next = all.items().stream().filter(item -> item.status().equals("OPEN")).findFirst().orElseThrow();
        assertEquals(task.seriesId(), next.seriesId());
        assertEquals(2, next.seriesSequence());
        assertNotEquals(task.publicId(), next.publicId());
        assertTrue(reminders.list(task.publicId()).isEmpty());
        assertEquals(1, reminders.list(next.publicId()).size());
        assertEquals(reminder.offsetMinutes(), reminders.list(next.publicId()).get(0).offsetMinutes());
        var reopenConflict = assertThrows(com.salarytracker.platform.ConflictException.class,
                () -> tasks.reopen(task.publicId(), String.valueOf(completed.revision())));
        assertTrue(reopenConflict.getMessage().contains("下一实例已生成"));

        assertEquals(2, tasks.listTasks(null, null, "ALL", null, 0, 20).total());
    }

    @Test
    void monthlyLastWorkdayRulePersistsAndGeneratesNextDatabaseInstance() {
        long userId = createUser();
        CurrentUserResolver current = current(userId);
        TaskService tasks = new TaskService(jdbc, current, new TaskChangeLog(jdbc,
                new ObjectMapper().findAndRegisterModules()));
        String rule = "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1";
        var task = tasks.create(new TaskCommand(null, "月末工作日", "", "NONE", null,
                "2026-10-30T09:00:00Z", false, "UTC", null, null, null, null, rule, "DUE_DATE"), "monthly-workday");

        assertEquals(rule, jdbc.queryForObject("SELECT rrule FROM task WHERE public_id=?", String.class, task.publicId()));
        tasks.complete(task.publicId(), String.valueOf(task.revision()));

        var next = tasks.listTasks(null, null, "ALL", null, 0, 20).items().stream()
                .filter(item -> item.seriesSequence() != null && item.seriesSequence() == 2)
                .findFirst().orElseThrow();
        assertEquals(Instant.parse("2026-11-30T09:00:00Z"), Instant.parse(next.plannedDueAt()));
        assertEquals(rule, next.rrule());
    }

    /** 复刻 scanDueRecurrences 的预生成：只建任务行，不带检查项 */
    private String insertPreGeneratedNext(String userTaskPublicId, String seriesId, String rule, String plannedDueAt) {
        Instant next = TaskRecurrence.next(rule, Instant.parse(plannedDueAt), Instant.now(), "DUE_DATE", "UTC", 2);
        String nextId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO task(public_id,user_id,list_id,title,description,status,priority,all_day,timezone," +
                        "series_id,series_sequence,planned_due_at,due_at,rrule,recurrence_anchor,source,created_by,client_op_key) " +
                        "SELECT ?,user_id,list_id,title,description,'OPEN',priority,all_day,timezone,series_id,2,?,?," +
                        "rrule,recurrence_anchor,'RECURRENCE',created_by,? FROM task WHERE public_id=?",
                nextId, Timestamp.from(next), Timestamp.from(next), "recurrence:" + seriesId + ":" + next, userTaskPublicId);
        return nextId;
    }

    /**
     * 下一实例在到期当天凌晨就预生成了，用户当天下午补的检查项要能追上，否则「下一周期没有检查项」。
     */
    @Test
    void checklistAddedAfterNextInstanceWasPreGeneratedStillReachesIt() {
        long userId = createUser();
        CurrentUserResolver current = current(userId);
        TaskService tasks = new TaskService(jdbc, current, new TaskChangeLog(jdbc,
                new ObjectMapper().findAndRegisterModules()));
        var task = tasks.create(new TaskCommand(null, "每日打卡", "", "NONE", null,
                Instant.now().minusSeconds(60).toString(), false, "UTC", null, null, null, null,
                "FREQ=DAILY", "DUE_DATE"), "repeat-checklist");
        String nextId = insertPreGeneratedNext(task.publicId(), task.seriesId(), "FREQ=DAILY", task.plannedDueAt());
        long revisionBefore = jdbc.queryForObject("SELECT revision FROM task WHERE public_id=?", Long.class, nextId);
        long oplogBefore = jdbc.queryForObject("SELECT COUNT(*) FROM task_sync_oplog WHERE user_id=? AND entity_id=?",
                Long.class, userId, nextId);

        tasks.update(task.publicId(), new TaskCommand(null, "每日打卡", "", "NONE", null,
                task.dueAt(), false, "UTC", null, null, null,
                List.of(new TaskModels.ChecklistCommand(null, "打卡", false, 0),
                        new TaskModels.ChecklistCommand(null, "喝水", false, 1)),
                "FREQ=DAILY", "DUE_DATE"), String.valueOf(task.revision()));

        assertEquals(List.of("打卡", "喝水"), checklistTitles(nextId));
        assertTrue(jdbc.queryForObject("SELECT revision FROM task WHERE public_id=?", Long.class, nextId) > revisionBefore,
                "下一实例要推高 revision 才能被各端拉到");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM task_sync_oplog WHERE user_id=? AND entity_id=?",
                Long.class, userId, nextId) > oplogBefore, "补齐检查项后要写同步日志");

        // 下一实例已有检查项时不再覆盖，避免冲掉用户手工加的内容
        jdbc.update("INSERT INTO task_checklist_item(public_id,user_id,task_id,title,completed,sort_order) " +
                "SELECT UUID(),?,id,'手工加的一条',FALSE,9 FROM task WHERE public_id=?", userId, nextId);
        long sourceRevision = jdbc.queryForObject("SELECT revision FROM task WHERE public_id=?", Long.class, task.publicId());
        tasks.update(task.publicId(), new TaskCommand(null, "每日打卡", "", "NONE", null,
                task.dueAt(), false, "UTC", null, null, null,
                List.of(new TaskModels.ChecklistCommand(null, "只留一条", false, 0)),
                "FREQ=DAILY", "DUE_DATE"), String.valueOf(sourceRevision));
        assertEquals(List.of("打卡", "喝水", "手工加的一条"), checklistTitles(nextId));
    }

    /**
     * 下一实例已存在时完成当前实例，插入被唯一键挡回；这条路径也要把检查项补上。
     */
    @Test
    void completingWithPreGeneratedNextInstanceBackfillsChecklist() {
        long userId = createUser();
        CurrentUserResolver current = current(userId);
        TaskService tasks = new TaskService(jdbc, current, new TaskChangeLog(jdbc,
                new ObjectMapper().findAndRegisterModules()));
        var task = tasks.create(new TaskCommand(null, "每日复盘", "", "NONE", null,
                Instant.now().minusSeconds(60).toString(), false, "UTC", null, null, null, null,
                "FREQ=DAILY", "DUE_DATE"), "repeat-backfill");
        String nextId = insertPreGeneratedNext(task.publicId(), task.seriesId(), "FREQ=DAILY", task.plannedDueAt());
        // 模拟旧客户端：直接写库加检查项，不走 update 的同步逻辑
        jdbc.update("INSERT INTO task_checklist_item(public_id,user_id,task_id,title,completed,sort_order) " +
                "SELECT UUID(),?,id,'回顾昨天',FALSE,0 FROM task WHERE public_id=?", userId, task.publicId());

        tasks.complete(task.publicId(), String.valueOf(task.revision()));

        assertEquals(List.of("回顾昨天"), checklistTitles(nextId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=? AND series_id=?",
                Long.class, userId, task.seriesId()), "不应再插入重复实例");
    }

    private List<String> checklistTitles(String taskPublicId) {
        return jdbc.query("SELECT c.title FROM task_checklist_item c JOIN task t ON t.id=c.task_id " +
                        "WHERE t.public_id=? AND c.deleted=FALSE ORDER BY c.sort_order,c.id",
                (row, rowNum) -> row.getString(1), taskPublicId);
    }

    @Test
    void reminderFireIsIdempotentAndUnreadCountBalances() {
        long userId = createUser();
        CurrentUserResolver current = current(userId);
        TaskService tasks = new TaskService(jdbc, current, new TaskChangeLog(jdbc,
                new ObjectMapper().findAndRegisterModules()));
        TaskReminderService reminders = new TaskReminderService(jdbc, current, new TaskInboxStream());
        var task = tasks.create(new TaskCommand(null, "缴费", null, null, null, null, null, null, null), "remind-create");
        var reminder = reminders.create(task.publicId(), new ReminderCommand("ABSOLUTE", null,
                "2026-10-10T00:00:00Z", "IN_APP", false));
        long id = jdbc.queryForObject("SELECT id FROM task_reminder WHERE public_id=?", Long.class, reminder.publicId());
        reminders.fire(id);
        reminders.fire(id);
        assertEquals(1, reminders.inbox(false, 0, 20).total());
        assertEquals(1, reminders.unread().unread());
        reminders.read(new TaskModels.InboxReadCommand(null, true));
        assertEquals(0, reminders.unread().unread());
        assertEquals(1, reminders.clearRead());
    }

    private CurrentUserResolver current(long userId) {
        CurrentUserResolver current = mock(CurrentUserResolver.class);
        when(current.id()).thenReturn(userId);
        return current;
    }

    private long createUser() {
        String username = "task-reminder-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '提醒测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
