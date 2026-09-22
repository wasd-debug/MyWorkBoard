package com.salarytracker.ai.session;

import com.salarytracker.identity.CurrentUserResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentConversationServiceTest {
    private final AgentSessionRepository repository = mock(AgentSessionRepository.class);
    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    private final AgentConversationService service = new AgentConversationService(repository, currentUser);

    @Test
    void createsSessionAndLoadsOnlyBoundedRecentContext() {
        when(currentUser.id()).thenReturn(7L);
        when(repository.findOwner("conversation-1")).thenReturn(OptionalLong.empty());
        when(repository.recentMessages("conversation-1", 7L, AgentConversationService.CONTEXT_MESSAGE_LIMIT))
                .thenReturn(List.of(new AgentConversationService.StoredMessage("assistant", "上一轮回答")));

        AgentConversationService.SessionContext context = service.open("conversation-1", "第一条问题");

        assertEquals("conversation-1", context.sessionId());
        assertEquals(1, context.history().size());
        verify(repository).create("conversation-1", 7L, "第一条问题");
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        when(currentUser.id()).thenReturn(7L);
        when(repository.findOwner("private-session")).thenReturn(OptionalLong.of(8L));

        assertThrows(IllegalArgumentException.class,
                () -> service.open("private-session", "读取别人的上下文"));

        verify(repository, never()).recentMessages("private-session", 7L,
                AgentConversationService.CONTEXT_MESSAGE_LIMIT);
    }

    @Test
    void persistsUserAndAssistantAsOneExchange() {
        AgentConversationService.SessionContext context =
                new AgentConversationService.SessionContext("conversation-1", 7L, List.of());

        service.complete(context, "它叫什么？", "默认账本。" );

        verify(repository).appendExchange("conversation-1", 7L, "它叫什么？", "默认账本。");
    }
}
