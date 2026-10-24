package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.task.TaskModels.Attachment;
import com.salarytracker.task.TaskModels.Comment;
import com.salarytracker.task.TaskModels.CommentCommand;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskCommentAttachmentIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void keepsCommentsPrivateIdempotentAndRevisionChecked() {
        long userId = createUser("comment-owner-");
        TaskService tasks = service(userId);
        TaskItem task = tasks.create(new TaskCommand(null, "评论契约", "", null, null, null, null, null, null), "comment-task");
        TaskCommentService comments = comments(userId, tasks);

        assertThrows(IllegalArgumentException.class,
                () -> comments.create(task.publicId(), new CommentCommand("[危险](javascript:alert(1))"), "comment-invalid"));
        Comment created = comments.create(task.publicId(), new CommentCommand("**重点** [文档](https://example.com)"), "comment-1");
        assertEquals(created.publicId(), comments.create(task.publicId(), new CommentCommand("不同正文"), "comment-1").publicId());
        Comment changed = comments.update(task.publicId(), created.publicId(), new CommentCommand("已修改"), "1");
        assertEquals(2L, changed.revision());
        assertThrows(ConflictException.class, () -> comments.update(task.publicId(), created.publicId(), new CommentCommand("过期"), "1"));
        comments.delete(task.publicId(), created.publicId(), "2");
        assertEquals(0, comments.list(task.publicId(), 0, 20).total());
    }

    @Test
    void storesAttachmentMetadataAndServesOriginalBytes() throws Exception {
        long userId = createUser("attachment-owner-");
        TaskService tasks = service(userId);
        TaskItem task = tasks.create(new TaskCommand(null, "附件契约", "", null, null, null, null, null, null), "attachment-task");
        Path root = Files.createTempDirectory("task-attachments-");
        TaskAttachmentService attachments = new TaskAttachmentService(jdbc, currentUser(userId), tasks, root.toString());
        byte[] bytes = "hello attachment".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", bytes);
        assertThrows(IllegalArgumentException.class, () -> attachments.upload(task.publicId(),
                new MockMultipartFile("file", "payload.svg", "application/octet-stream", bytes), "attachment-invalid"));
        Attachment created = attachments.upload(task.publicId(), file, "attachment-1");
        assertEquals(created.publicId(), attachments.upload(task.publicId(), file, "attachment-1").publicId());
        assertEquals(bytes.length, created.sizeBytes());
        assertEquals(1, tasks.get(task.publicId()).attachmentCount());
        try (var input = attachments.download(task.publicId(), created.publicId()).resource().getInputStream()) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        attachments.delete(task.publicId(), created.publicId(), "1");
        assertEquals(0, attachments.list(task.publicId()).size());
        assertEquals(0, tasks.get(task.publicId()).attachmentCount());
    }

    private TaskCommentService comments(long userId, TaskService tasks) {
        return new TaskCommentService(jdbc, currentUser(userId), tasks);
    }

    private TaskService service(long userId) {
        return new TaskService(jdbc, currentUser(userId), new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules()));
    }

    private CurrentUserResolver currentUser(long userId) {
        CurrentUserResolver resolver = org.mockito.Mockito.mock(CurrentUserResolver.class);
        org.mockito.Mockito.when(resolver.id()).thenReturn(userId);
        return resolver;
    }

    private long createUser(String prefix) {
        String username = prefix + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '任务测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }
}
