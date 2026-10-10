package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.SyncResetRequiredException;
import com.salarytracker.task.TaskModels.SyncAction;
import com.salarytracker.task.TaskModels.SyncOperation;
import com.salarytracker.task.TaskModels.SyncPayload;
import com.salarytracker.task.TaskModels.SyncStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskSyncIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void appliesCreateOnceAndReturnsDuplicateOnReplay() {
        Fixture fixture = fixture(createUser("sync-create-"));
        String taskId = UUID.randomUUID().toString();
        SyncOperation create = operation("op-create", taskId, SyncAction.UPSERT, 0,
                payload(taskId, "离线创建", "OPEN", 0));

        assertEquals(SyncStatus.APPLIED, fixture.sync.push(List.of(create)).results().get(0).status());
        assertEquals(SyncStatus.DUPLICATE, fixture.sync.push(List.of(create)).results().get(0).status());
        assertEquals(1, fixture.tasks.listTasks(null, null, 0, 20).total());
        assertEquals(taskId, fixture.tasks.listTasks(null, null, 0, 20).items().get(0).publicId());
    }

    @Test
    void exposesRevisionConflictAndKeepsServerVersion() {
        Fixture fixture = fixture(createUser("sync-conflict-"));
        String taskId = UUID.randomUUID().toString();
        fixture.sync.push(List.of(operation("create", taskId, SyncAction.UPSERT, 0,
                payload(taskId, "初始", "OPEN", 0))));
        fixture.sync.push(List.of(operation("update", taskId, SyncAction.UPSERT, 1,
                payload(taskId, "服务端更新", "OPEN", 1))));

        var result = fixture.sync.push(List.of(operation("stale", taskId, SyncAction.UPSERT, 1,
                payload(taskId, "离线旧更新", "COMPLETED", 1)))).results().get(0);

        assertEquals(SyncStatus.CONFLICT, result.status());
        assertEquals(2L, result.serverRevision());
        assertEquals("服务端更新", result.serverEntity().title());
        assertTrue(result.conflictFields().contains("title"));
    }

    @Test
    void pullsPagesAndTombstonesWithinCurrentUserOnly() {
        long alice = createUser("sync-alice-");
        long bob = createUser("sync-bob-");
        Fixture aliceFixture = fixture(alice);
        Fixture bobFixture = fixture(bob);
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        String privateTask = UUID.randomUUID().toString();
        aliceFixture.sync.push(List.of(
                operation("alice-1", first, SyncAction.UPSERT, 0, payload(first, "A1", "OPEN", 0)),
                operation("alice-2", second, SyncAction.UPSERT, 0, payload(second, "A2", "OPEN", 0))));
        bobFixture.sync.push(List.of(operation("bob-1", privateTask, SyncAction.UPSERT, 0,
                payload(privateTask, "B1", "OPEN", 0))));

        var firstPage = aliceFixture.sync.pull(0, 1);
        var secondPage = aliceFixture.sync.pull(firstPage.cursor(), 10);
        assertEquals(1, firstPage.operations().size());
        assertTrue(firstPage.hasMore());
        assertEquals(1, secondPage.operations().size());
        assertTrue(secondPage.operations().stream().noneMatch(item -> privateTask.equals(item.entityId())));

        aliceFixture.sync.push(List.of(operation("alice-delete", first, SyncAction.DELETE, 1, null)));
        var deleted = aliceFixture.sync.pull(secondPage.cursor(), 10).operations().get(0);
        assertEquals(SyncAction.DELETE, deleted.operation());
        assertTrue(deleted.payload().deleted());
    }

    @Test
    void rejectsInvalidAndForeignOperationsAndInvalidCursor() {
        long owner = createUser("sync-owner-");
        long outsider = createUser("sync-outsider-");
        Fixture ownerFixture = fixture(owner);
        Fixture outsiderFixture = fixture(outsider);
        String taskId = UUID.randomUUID().toString();
        ownerFixture.sync.push(List.of(operation("owner-create", taskId, SyncAction.UPSERT, 0,
                payload(taskId, "私有任务", "OPEN", 0))));

        var forbidden = outsiderFixture.sync.push(List.of(operation("foreign-delete", taskId,
                SyncAction.DELETE, 1, null))).results().get(0);
        var rejected = ownerFixture.sync.push(List.of(operation("invalid", UUID.randomUUID().toString(),
                SyncAction.UPSERT, 0, payload(null, "", "OPEN", 0)))).results().get(0);

        assertEquals(SyncStatus.FORBIDDEN, forbidden.status());
        assertEquals(SyncStatus.REJECTED, rejected.status());
        assertThrows(SyncResetRequiredException.class, () -> ownerFixture.sync.pull(999999, 20));
    }

    private SyncOperation operation(String opId, String entityId, SyncAction action, long revision, SyncPayload payload) {
        return new SyncOperation(opId, "task", entityId, action, revision, payload);
    }

    private SyncPayload payload(String id, String title, String status, long revision) {
        return new SyncPayload(id, null, title, "", status, "NONE", null, null,
                false, "Asia/Shanghai", null, revision);
    }

    private Fixture fixture(long userId) {
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(userId);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TaskService tasks = new TaskService(jdbc, resolver, new TaskChangeLog(jdbc, mapper));
        return new Fixture(tasks, new TaskSyncService(jdbc, mapper, resolver, tasks));
    }

    private long createUser(String prefix) {
        String username = prefix + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '同步测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private record Fixture(TaskService tasks, TaskSyncService sync) {
    }
}
