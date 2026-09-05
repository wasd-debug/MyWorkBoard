package com.salarytracker.platform;

public record ApiResponse<T>(T data, String traceId) {
    public static <T> ApiResponse<T> of(T data, String traceId) {
        return new ApiResponse<>(data, traceId);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, TraceIds.current());
    }
}
