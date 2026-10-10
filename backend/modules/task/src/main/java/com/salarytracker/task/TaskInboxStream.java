package com.salarytracker.task;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
class TaskInboxStream {
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    SseEmitter connect(long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> emitters.getOrDefault(userId, new CopyOnWriteArrayList<>()).remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        send(emitter, "connected", Map.of("connected", true));
        return emitter;
    }

    void publish(long userId, TaskModels.InboxMessage message) {
        for (SseEmitter emitter : List.copyOf(emitters.getOrDefault(userId, new CopyOnWriteArrayList<>()))) {
            send(emitter, "message", message);
        }
    }

    void heartbeat() {
        emitters.values().forEach(list -> List.copyOf(list).forEach(emitter -> send(emitter, "heartbeat", Map.of("ok", true))));
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try { emitter.send(SseEmitter.event().name(event).data(data)); }
        catch (IOException exception) { emitter.complete(); }
    }
}
