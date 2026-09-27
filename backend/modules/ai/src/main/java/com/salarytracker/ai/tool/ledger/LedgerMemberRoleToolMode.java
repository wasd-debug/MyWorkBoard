package com.salarytracker.ai.tool.ledger;

enum LedgerMemberRoleToolMode {
    MEMBER_CREATE("ledger.member.create", "成员", "添加成员"),
    MEMBER_UPDATE("ledger.member.update", "成员", "修改成员"),
    MEMBER_DELETE("ledger.member.delete", "成员", "移除成员"),
    ROLE_CREATE("ledger.role.create", "角色", "创建角色"),
    ROLE_UPDATE("ledger.role.update", "角色", "修改角色"),
    ROLE_DELETE("ledger.role.delete", "角色", "删除角色");

    private final String actionType;
    private final String resourceLabel;
    private final String actionLabel;

    LedgerMemberRoleToolMode(String actionType, String resourceLabel, String actionLabel) {
        this.actionType = actionType;
        this.resourceLabel = resourceLabel;
        this.actionLabel = actionLabel;
    }

    String actionType() { return actionType; }
    String resourceLabel() { return resourceLabel; }
    String actionLabel() { return actionLabel; }
    boolean member() { return name().startsWith("MEMBER_"); }
    boolean role() { return name().startsWith("ROLE_"); }
    boolean create() { return name().endsWith("_CREATE"); }
    boolean update() { return name().endsWith("_UPDATE"); }
    boolean delete() { return name().endsWith("_DELETE"); }
}
