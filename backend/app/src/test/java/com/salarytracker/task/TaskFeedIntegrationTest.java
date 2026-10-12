package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskFeedIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void feedStoresOnlyHashAndRotationRevokesOldTokenImmediately() {
        long userId = createUser();
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(userId);
        TaskService tasks = new TaskService(jdbc, resolver, new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules()));
        var list = tasks.createList(new TaskModels.TaskListCommand("订阅", null, null, 1, false), "feed-list");
        var task = tasks.create(new TaskModels.TaskCommand(list.publicId(), "公开标题", "私密描述", "HIGH", null,
                Instant.now().plusSeconds(3600).toString(), false, "Asia/Shanghai", null), "feed-task");
        TaskReminderService reminders = new TaskReminderService(jdbc, resolver, new TaskInboxStream());
        reminders.create(task.publicId(), new TaskModels.ReminderCommand("RELATIVE", 30, null, "IN_APP", false));
        TaskFeedService feeds = new TaskFeedService(jdbc, resolver);

        var created = feeds.create("https://work.example");
        assertTrue(created.feedUrl().startsWith("https://work.example/api/v1/tasks/feed/"));
        String raw = created.feedUrl().substring(created.feedUrl().lastIndexOf('/') + 1).replace(".ics", "");
        assertFalse(jdbc.queryForObject("SELECT token_hash FROM task_feed_token WHERE user_id=? AND revoked_at IS NULL", String.class, userId).contains(raw));
        String calendar = feeds.feed(raw, "127.0.0.1");
        assertTrue(calendar.contains("SUMMARY:公开标题"));
        assertTrue(calendar.contains("BEGIN:VALARM"));
        assertFalse(calendar.contains("私密描述"));

        var rotated = feeds.rotate("https://work.example");
        assertThrows(RuntimeException.class, () -> feeds.feed(raw, "127.0.0.1"));
        String next = rotated.feedUrl().substring(rotated.feedUrl().lastIndexOf('/') + 1).replace(".ics", "");
        assertTrue(feeds.feed(next, "127.0.0.1").contains("公开标题"));
        assertNotNull(feeds.status().lastAccessedAt());
        feeds.revoke();
        assertThrows(RuntimeException.class, () -> feeds.feed(next, "127.0.0.1"));
    }

    private long createUser() {
        String username = "feed-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '订阅测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
