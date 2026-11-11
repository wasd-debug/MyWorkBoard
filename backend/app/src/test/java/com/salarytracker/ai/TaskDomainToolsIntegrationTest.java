package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.JdbcPendingActionRepository;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.ai.tool.task.TaskToolsConfiguration;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.task.TaskModels;
import com.salarytracker.task.TaskService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskDomainToolsIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void prepareDoesNotWriteAndApprovedCommitIsSingleUse() {
        long userId=createUser(); CurrentUserResolver resolver=mock(CurrentUserResolver.class);when(resolver.id()).thenReturn(userId);
        ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();TaskService tasks=mock(TaskService.class);
        when(tasks.create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new TaskModels.TaskItem(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"Agent 创建","","OPEN","HIGH",null,null,false,"Asia/Shanghai",null,"AGENT",null,1));
        PendingActionService actions=new PendingActionService(new JdbcPendingActionRepository(jdbc),mapper,new InteractionPolicy());
        TaskToolsConfiguration tools=new TaskToolsConfiguration();var prepare=tools.taskCreatePrepareTool(tasks,actions,resolver,mapper);
        var commit=tools.taskCreateCommitTool(tasks,actions,resolver,mapper);
        var result=prepare.execute(mapper.createObjectNode().put("title","Agent 创建").put("priority","HIGH"));
        assertEquals(ToolStatus.NEEDS_CONFIRMATION,result.status());verify(tasks,never()).create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
        assertEquals("新建任务：Agent 创建（收件箱）",result.summary());
        assertEquals("task.create",result.structuredContent().path("actionType").asText());
        assertEquals("Agent 创建",result.structuredContent().path("preview").path("title").asText());
        assertEquals("收件箱",result.structuredContent().path("preview").path("listName").asText());
        assertEquals("高",result.structuredContent().path("preview").path("priority").asText());
        actions.approve(result.actionId(),userId);var created=commit.execute(mapper.createObjectNode().put("actionId",result.actionId()));
        assertEquals(ToolStatus.COMPLETED,created.status());verify(tasks,times(1)).create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
        assertEquals(ToolStatus.COMPLETED,commit.execute(mapper.createObjectNode().put("actionId",result.actionId())).status());
        verify(tasks,times(1)).create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }
    /** 卡片必须自带人类可读标题与结构化预览，否则前端只能按账本字段猜（曾显示“类型待补充”）。 */
    @Test
    void prepareCardCarriesReadableSummaryAndTaskPreview() {
        long userId=createUser(); CurrentUserResolver resolver=mock(CurrentUserResolver.class);when(resolver.id()).thenReturn(userId);
        ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();TaskService tasks=mock(TaskService.class);
        String listId=UUID.randomUUID().toString();String taskId=UUID.randomUUID().toString();
        when(tasks.get(taskId)).thenReturn(new TaskModels.TaskItem(taskId,listId,"交周报","",  "OPEN","NONE",
                null,"2026-11-12T15:00:00+08:00",false,"Asia/Shanghai",null,"MANUAL",null,3));
        when(tasks.listLists()).thenReturn(java.util.List.of(
                new TaskModels.TaskList(listId,null,"👨🏻‍💻工作",null,null,null,0,false,1)));
        PendingActionService actions=new PendingActionService(new JdbcPendingActionRepository(jdbc),mapper,new InteractionPolicy());
        TaskToolsConfiguration tools=new TaskToolsConfiguration();

        var complete=tools.taskCompletePrepareTool(tasks,actions,resolver,mapper)
                .execute(mapper.createObjectNode().put("taskId",taskId));
        assertEquals("完成任务：交周报（👨🏻‍💻工作）",complete.summary());
        assertEquals("task.complete",complete.structuredContent().path("actionType").asText());
        assertEquals("👨🏻‍💻工作",complete.structuredContent().path("preview").path("listName").asText());
        assertEquals("2026-11-12 15:00",complete.structuredContent().path("preview").path("dueAt").asText());

        var deleted=tools.taskDeletePrepareTool(tasks,actions,resolver,mapper)
                .execute(mapper.createObjectNode().put("taskId",taskId));
        assertEquals("删除任务：交周报（👨🏻‍💻工作）",deleted.summary());
        assertEquals("task.delete",deleted.structuredContent().path("actionType").asText());
    }

    private long createUser(){String username="task-tool-"+UUID.randomUUID();jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?,'!','工具测试')",username);return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,username);}
}
