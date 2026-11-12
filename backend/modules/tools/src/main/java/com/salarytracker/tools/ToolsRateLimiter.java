package com.salarytracker.tools;

import com.salarytracker.platform.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单用户滑动窗口限流（进程内）。抖音解析会对外发请求，必须有一个廉价的刹车，
 * 避免被当成爬虫或把出口 IP 打满。
 */
@Component
public class ToolsRateLimiter {
    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_USERS = 5000;

    private final Map<Long, Deque<Long>> windows = new ConcurrentHashMap<>();

    public void check(long userId) {
        long now = System.nanoTime();
        if (windows.size() > MAX_TRACKED_USERS) evictIdle(now);
        Deque<Long> stamps = windows.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (stamps) {
            long windowNanos = WINDOW.toNanos();
            while (!stamps.isEmpty() && now - stamps.peekFirst() > windowNanos) stamps.pollFirst();
            if (stamps.size() >= LIMIT) {
                throw new TooManyRequestsException("操作过于频繁，请稍后再试");
            }
            stamps.addLast(now);
        }
    }

    private void evictIdle(long now) {
        long windowNanos = WINDOW.toNanos();
        windows.entrySet().removeIf(entry -> {
            Deque<Long> stamps = entry.getValue();
            synchronized (stamps) {
                return stamps.isEmpty() || now - stamps.peekLast() > windowNanos;
            }
        });
    }
}
