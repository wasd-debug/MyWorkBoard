package com.salarytracker.platform;

public class SyncResetRequiredException extends RuntimeException {
    public SyncResetRequiredException(String message) {
        super(message);
    }
}
