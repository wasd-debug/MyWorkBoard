package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.approval.AgentApproval;
import com.salarytracker.ai.approval.AgentApprovalService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Member;
import com.salarytracker.ledger.LedgerModels.MemberCandidate;
import com.salarytracker.ledger.LedgerModels.Role;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class LedgerMemberRolePrepareTool implements DomainTool {
    private static final Set<String> ICONS = Set.of("user", "work", "home", "trophy", "chat", "other");
    private final LedgerMemberRoleToolMode mode;
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final AgentApprovalService approvals;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerMemberRolePrepareTool(LedgerMemberRoleToolMode mode, LedgerBookService books,
                                PendingActionService actions, AgentApprovalService approvals,
                                CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.mode = mode;
        this.books = books;
        this.actions = actions;
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        if (mode.member()) {
            if (mode.create()) ToolSchemas.stringProperty(schema, "username", "已注册用户名", null);
            else ToolSchemas.stringProperty(schema, "memberId", "成员公开 ID", null);
            if (!mode.delete()) {
                ToolSchemas.stringProperty(schema, "roleId", "目标角色公开 ID", null);
                ToolSchemas.stringProperty(schema, "icon", "成员图标代码", null);
            }
        } else {
            if (!mode.create()) ToolSchemas.stringProperty(schema, "roleId", "角色公开 ID", null);
            if (!mode.delete()) {
                ToolSchemas.stringProperty(schema, "name", "角色名称", null);
                ToolSchemas.arrayProperty(schema, "permissions", "角色权限代码列表",
                        mapper.createObjectNode().put("type", "string"), 0, 20);
            }
        }
        List<String> required = new ArrayList<>(List.of("bookId"));
        if (mode.member()) {
            required.add(mode.create() ? "username" : "memberId");
            if (!mode.delete()) required.add("roleId");
        } else {
            if (!mode.create()) required.add("roleId");
            if (!mode.delete()) required.addAll(List.of("name", "permissions"));
        }
        ToolSchemas.required(schema, required.toArray(String[]::new));
        definition = new ToolDefinition(mode.actionType() + ".prepare", 1,
                mode.actionLabel() + "前创建 R4 站内审批，不直接写入业务数据。",
                ToolRisk.R4, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        ObjectNode normalized = mapper.createObjectNode().put("bookId", bookId);
        Object before;
        Object after;
        Long revision = null;
        ArrayNode effects = mapper.createArrayNode();

        if (mode.member()) {
            List<Role> roles = books.roles(bookId);
            if (mode.create()) {
                String username = ToolInputs.requiredText(input, "username");
                Role role = role(roles, ToolInputs.requiredText(input, "roleId"));
                MemberCandidate candidate = books.memberCandidate(bookId, username);
                String icon = icon(ToolInputs.optionalText(input, "icon"));
                normalized.put("username", candidate.username()).put("roleId", role.id()).put("icon", icon);
                before = null;
                after = mapper.createObjectNode().put("username", candidate.username())
                        .put("displayName", candidate.displayName()).put("roleId", role.id())
                        .put("roleName", role.name()).put("icon", icon);
                effects.add("该用户将获得角色“" + role.name() + "”包含的账本权限");
                effects.add("批准前会再次校验用户名、账本权限和成员唯一性");
            } else {
                Member member = member(books.members(bookId), ToolInputs.requiredText(input, "memberId"));
                if ("OWNER".equals(member.roleCode())) throw new IllegalArgumentException("不能修改或移除账本主人");
                normalized.put("memberId", member.id());
                before = member;
                revision = member.revision();
                if (mode.update()) {
                    Role role = role(roles, ToolInputs.requiredText(input, "roleId"));
                    String icon = icon(ToolInputs.optionalText(input, "icon"));
                    normalized.put("roleId", role.id()).put("icon", icon);
                    after = mapper.createObjectNode().put("username", member.username())
                            .put("displayName", member.displayName()).put("roleId", role.id())
                            .put("roleName", role.name()).put("icon", icon);
                    effects.add("成员角色将从“" + member.roleName() + "”调整为“" + role.name() + "”");
                } else {
                    after = null;
                    long transactions = books.memberTransactionCount(bookId, member.id());
                    effects.add("成员将无法继续访问当前账本");
                    effects.add("该成员关联的 " + transactions + " 笔历史流水会保留");
                }
            }
        } else {
            List<Role> roles = books.roles(bookId);
            if (mode.create()) {
                String name = ToolInputs.requiredText(input, "name");
                List<String> permissions = permissions(input);
                normalized.put("name", name).set("permissions", mapper.valueToTree(permissions));
                before = null;
                after = mapper.createObjectNode().put("name", name).set("permissions", mapper.valueToTree(permissions));
                effects.add("新角色创建后可分配给当前账本成员");
            } else {
                Role role = role(roles, ToolInputs.requiredText(input, "roleId"));
                if (role.systemRole()) throw new IllegalArgumentException("系统角色不可编辑或删除");
                normalized.put("roleId", role.id());
                before = role;
                revision = role.revision();
                if (mode.update()) {
                    String name = ToolInputs.requiredText(input, "name");
                    List<String> permissions = permissions(input);
                    normalized.put("name", name).set("permissions", mapper.valueToTree(permissions));
                    after = mapper.createObjectNode().put("name", name).set("permissions", mapper.valueToTree(permissions));
                    effects.add("权限变化会应用到所有使用该角色的成员");
                } else {
                    after = null;
                    long members = books.roleMemberCount(bookId, role.id());
                    if (members > 0) throw new IllegalArgumentException("请先为使用该角色的成员更换角色");
                    effects.add("角色删除后不可再分配给成员");
                }
            }
        }

        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, revision,
                false, Duration.ofMinutes(30));
        ObjectNode payload = mapper.createObjectNode();
        payload.put("actionType", mode.actionType());
        payload.put("resourceType", mode.member() ? "member" : "role");
        payload.put("operation", mode.create() ? "create" : mode.update() ? "update" : "delete");
        payload.set("input", normalized);
        payload.set("before", mapper.valueToTree(before));
        payload.set("after", mapper.valueToTree(after));
        payload.set("effects", effects);
        if (mode.update()) payload.set("diff", diff(before, after));
        payload.put("webApprovalRequired", true);
        payload.put("commitAvailable", false);
        AgentApproval approval = approvals.create(action, bookId, summary(before, after), payload);
        payload.put("approvalId", approval.id());
        return new ToolResult(com.salarytracker.ai.tool.ToolStatus.NEEDS_CONFIRMATION,
                "已创建高风险审批，请前往站内审批中心处理", payload, action.id(),
                "/approvals/" + approval.id(), action.expiresAt().toString(), null);
    }

    private List<String> permissions(JsonNode input) {
        JsonNode values = input.get("permissions");
        if (values == null || !values.isArray()) throw new IllegalArgumentException("permissions 必须是数组");
        List<String> requested = new ArrayList<>();
        values.forEach(value -> {
            if (!value.isTextual()) throw new IllegalArgumentException("权限代码必须是字符串");
            requested.add(value.asText());
        });
        return books.validateCustomRolePermissions(requested);
    }

    private Member member(List<Member> members, String id) {
        return members.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("成员不存在"));
    }

    private Role role(List<Role> roles, String id) {
        return roles.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
    }

    private String icon(String value) {
        String normalized = value == null ? "user" : value;
        return ICONS.contains(normalized) ? normalized : "user";
    }

    private ObjectNode diff(Object before, Object after) {
        ObjectNode value = mapper.createObjectNode();
        value.set("before", mapper.valueToTree(before));
        value.set("after", mapper.valueToTree(after));
        return value;
    }

    private String summary(Object before, Object after) {
        if (mode.member()) {
            JsonNode value = mapper.valueToTree(after == null ? before : after);
            return mode.actionLabel() + "“" + value.path("displayName").asText(value.path("username").asText()) + "”";
        }
        JsonNode value = mapper.valueToTree(after == null ? before : after);
        return mode.actionLabel() + "“" + value.path("name").asText() + "”";
    }
}
