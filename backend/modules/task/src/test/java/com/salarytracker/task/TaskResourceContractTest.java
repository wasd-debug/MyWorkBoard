package com.salarytracker.task;

import com.salarytracker.task.TaskModels.DeletedResource;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(TaskResourceContractTest.MethodSecurity.class)
@ContextConfiguration(classes = {TaskController.class, TaskResourceContractTest.MethodSecurity.class})
class TaskResourceContractTest {
    private static final String TASK_ID = "22222222-2222-2222-2222-222222222222";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private TaskSyncService taskSyncService;

    @MockBean
    private TaskReminderService taskReminderService;

    @Test
    void rejectsAnonymousReads() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/lists")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "task:read")
    void rejectsWritesWithoutTaskWriteAuthority() throws Exception {
        mockMvc.perform(post("/api/v1/tasks").with(csrf()).header("Idempotency-Key", "create-1")
                        .contentType("application/json").content("{\"title\":\"整理收件箱\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "task:write")
    void requiresConcurrencyHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/tasks").with(csrf()).contentType("application/json")
                        .content("{\"title\":\"整理收件箱\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/tasks/" + TASK_ID).with(csrf()).contentType("application/json")
                        .content("{\"title\":\"整理收件箱\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "task:write")
    void forwardsIdempotencyAndRevisionHeadersAcrossLifecycle() throws Exception {
        TaskItem created = item("OPEN", 1L);
        TaskItem changed = item("COMPLETED", 2L);
        when(taskService.create(any(TaskCommand.class), eq("create-1"))).thenReturn(created);
        when(taskService.update(eq(TASK_ID), any(TaskCommand.class), eq("1"))).thenReturn(item("OPEN", 2L));
        when(taskService.complete(TASK_ID, "2")).thenReturn(changed);
        when(taskService.reopen(TASK_ID, "2")).thenReturn(item("OPEN", 3L));
        when(taskService.delete(TASK_ID, "3")).thenReturn(new DeletedResource(TASK_ID, 4L, true));

        mockMvc.perform(post("/api/v1/tasks").with(csrf()).header("Idempotency-Key", "create-1")
                        .contentType("application/json").content("{\"title\":\"整理收件箱\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publicId").value(TASK_ID));
        mockMvc.perform(put("/api/v1/tasks/" + TASK_ID).with(csrf()).header("If-Match", "1")
                        .contentType("application/json").content("{\"title\":\"整理任务\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(2));
        mockMvc.perform(post("/api/v1/tasks/" + TASK_ID + "/complete").with(csrf()).header("If-Match", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mockMvc.perform(post("/api/v1/tasks/" + TASK_ID + "/reopen").with(csrf()).header("If-Match", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OPEN"));
        mockMvc.perform(delete("/api/v1/tasks/" + TASK_ID).with(csrf()).header("If-Match", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deleted").value(true));

        verify(taskService).create(any(TaskCommand.class), eq("create-1"));
        verify(taskService).complete(TASK_ID, "2");
    }

    @Test
    @WithMockUser(authorities = {"task:read", "task:write"})
    void exposesUserScopedSyncEndpoints() throws Exception {
        when(taskSyncService.push(anyList())).thenReturn(new TaskModels.SyncPushResponse(List.of(), 0, 0));
        when(taskSyncService.pull(0, 20)).thenReturn(new TaskModels.SyncPullResponse(List.of(), 0, false, 0));

        mockMvc.perform(post("/api/v1/tasks/sync/push").with(csrf()).contentType("application/json")
                        .content("[]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(0));
        mockMvc.perform(get("/api/v1/tasks/sync/pull").param("cursor", "0").param("limit", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMore").value(false));
    }

    private TaskItem item(String status, long revision) {
        return new TaskItem(TASK_ID, "11111111-1111-1111-1111-111111111111", "整理收件箱", "", status,
                "NONE", null, null, false, "Asia/Shanghai", null, "WEB", null, revision);
    }
}
