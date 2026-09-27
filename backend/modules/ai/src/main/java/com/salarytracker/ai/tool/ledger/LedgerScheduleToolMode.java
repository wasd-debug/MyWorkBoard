package com.salarytracker.ai.tool.ledger;

enum LedgerScheduleToolMode {
    CREATE("ledger.schedule.create", "创建周期任务"),
    UPDATE("ledger.schedule.update", "修改周期任务"),
    DELETE("ledger.schedule.delete", "删除周期任务"),
    RUN("ledger.schedule.run", "立即执行周期任务");

    private final String actionType;
    private final String label;

    LedgerScheduleToolMode(String actionType, String label) {
        this.actionType = actionType;
        this.label = label;
    }

    String actionType() { return actionType; }
    String label() { return label; }
    boolean editable() { return this == CREATE || this == UPDATE; }
}
