package com.salarytracker.platform;

public class ConflictException extends RuntimeException {
    private final long serverRevision;

    public ConflictException(String message, long serverRevision) {
        super(message);
        this.serverRevision = serverRevision;
    }

    public long getServerRevision() {
        return serverRevision;
    }
}
