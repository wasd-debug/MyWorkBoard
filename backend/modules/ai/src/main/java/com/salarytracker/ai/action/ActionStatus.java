package com.salarytracker.ai.action;

public enum ActionStatus {
    RECEIVED,
    PLANNING,
    WAITING_INPUT,
    WAITING_CONFIRMATION,
    APPROVED,
    EXECUTING,
    COMPLETED,
    CONFLICT,
    DENIED,
    FAILED,
    CANCELLED,
    EXPIRED
}
