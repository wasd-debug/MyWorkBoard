package com.salarytracker.platform;

/**
 * 触发限流。映射为 429，前端据此提示「稍后再试」而不是报错重试。
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
