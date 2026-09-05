package com.salarytracker.platform;

import java.util.UUID;

public final class TraceIds {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceIds() {
    }

    public static String current() {
        String value = CURRENT.get();
        if (value == null) {
            value = UUID.randomUUID().toString();
            CURRENT.set(value);
        }
        return value;
    }

    public static void set(String value) {
        CURRENT.set(value == null || value.isBlank() ? UUID.randomUUID().toString() : value);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
