package com.salarytracker.ledger;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ForbiddenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class LedgerBookAccess {
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;

    public LedgerBookAccess(JdbcTemplate jdbc, CurrentUserResolver currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Transactional
    public String ensureDefaultBook() {
        long userId = currentUser.id();
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT b.public_id FROM ledger_book b JOIN ledger_book_member m ON m.book_id=b.id " +
                        "WHERE m.user_id=? AND m.deleted=FALSE AND b.deleted=FALSE ORDER BY b.created_at,b.id LIMIT 1",
                userId);
        if (!existing.isEmpty()) return String.valueOf(existing.get(0).get("public_id"));

        String bookPublicId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ledger_book(public_id,owner_user_id,name) VALUES(?,?,'默认账本')", bookPublicId, userId);
        long bookId = jdbc.queryForObject("SELECT id FROM ledger_book WHERE public_id=?", Long.class, bookPublicId);
        seedRolesAndOwner(bookId, userId);
        return bookPublicId;
    }

    public Context resolve(String publicId) {
        String selected = publicId;
        if (selected == null || selected.isBlank() || "default".equalsIgnoreCase(selected)) {
            selected = ensureDefaultBook();
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT b.id book_id,b.public_id,b.owner_user_id,m.id member_id,m.role_id,r.code role_code " +
                        "FROM ledger_book b JOIN ledger_book_member m ON m.book_id=b.id " +
                        "JOIN ledger_role r ON r.id=m.role_id " +
                        "WHERE b.public_id=? AND b.deleted=FALSE AND m.user_id=? AND m.deleted=FALSE AND r.deleted=FALSE",
                selected, currentUser.id());
        if (rows.isEmpty()) throw new ForbiddenException("无权访问该账本");
        Map<String, Object> row = rows.get(0);
        long roleId = number(row.get("role_id"));
        Set<String> permissions = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT permission_code FROM ledger_role_permission WHERE role_id=?", String.class, roleId));
        return new Context(
                number(row.get("book_id")),
                String.valueOf(row.get("public_id")),
                currentUser.id(),
                number(row.get("owner_user_id")),
                number(row.get("member_id")),
                roleId,
                String.valueOf(row.get("role_code")),
                permissions);
    }

    public void require(Context context, String permission) {
        if (context.isOwner() || context.permissions().contains(permission)) return;
        throw new ForbiddenException("当前角色缺少权限：" + permission);
    }

    public void requireTransactionWrite(Context context, long createdBy) {
        if (context.isOwner() || context.permissions().contains("TRANSACTION_ANY_WRITE")) return;
        if (createdBy == context.userId() && context.permissions().contains("TRANSACTION_OWN_WRITE")) return;
        throw new ForbiddenException("无权修改他人的流水");
    }

    void seedRolesAndOwner(long bookId, long userId) {
        createRole(bookId, "OWNER", "账本主人", true, List.of(
                "BOOK_DELETE", "RESOURCE_MANAGE", "MEMBER_MANAGE", "ROLE_MANAGE",
                "TRANSACTION_ANY_WRITE", "TRANSACTION_OWN_WRITE", "AUDIT_ALL_READ",
                "AUDIT_ALL_CLEAR", "AUDIT_SELF_READ", "RECYCLE_ALL", "RECYCLE_SELF", "IMPORT_EXPORT"));
        createRole(bookId, "ADMIN", "管理员", true, List.of(
                "RESOURCE_MANAGE", "MEMBER_MANAGE", "ROLE_MANAGE", "TRANSACTION_ANY_WRITE",
                "TRANSACTION_OWN_WRITE", "AUDIT_ALL_READ", "AUDIT_ALL_CLEAR",
                "AUDIT_SELF_READ", "RECYCLE_ALL", "RECYCLE_SELF", "IMPORT_EXPORT"));
        createRole(bookId, "MEMBER", "成员", true, List.of(
                "TRANSACTION_OWN_WRITE", "AUDIT_SELF_READ", "RECYCLE_SELF"));
        Long ownerRoleId = jdbc.queryForObject(
                "SELECT id FROM ledger_role WHERE book_id=? AND code='OWNER'", Long.class, bookId);
        jdbc.update(
                "INSERT INTO ledger_book_member(public_id,book_id,user_id,role_id,created_by) VALUES(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE role_id=VALUES(role_id),deleted=FALSE,deleted_at=NULL,revision=revision+1",
                UUID.randomUUID().toString(), bookId, userId, ownerRoleId, userId);
    }

    private void createRole(long bookId, String code, String name, boolean system, List<String> permissions) {
        long ownerUserId = jdbc.queryForObject(
                "SELECT owner_user_id FROM ledger_book WHERE id=?", Long.class, bookId);
        jdbc.update(
                "INSERT INTO ledger_role(public_id,book_id,code,name,system_role,created_by) VALUES(?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE name=VALUES(name),deleted=FALSE,deleted_at=NULL",
                UUID.randomUUID().toString(), bookId, code, name, system, ownerUserId);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM ledger_role WHERE book_id=? AND code=?", Long.class, bookId, code);
        for (String permission : permissions) {
            jdbc.update("INSERT IGNORE INTO ledger_role_permission(role_id,permission_code) VALUES(?,?)",
                    roleId, permission);
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    public record Context(long bookId,
                          String bookPublicId,
                          long userId,
                          long ownerUserId,
                          long memberId,
                          long roleId,
                          String roleCode,
                          Set<String> permissions) {
        public boolean isOwner() {
            return userId == ownerUserId || "OWNER".equals(roleCode);
        }

        public boolean isAdmin() {
            return isOwner() || "ADMIN".equals(roleCode);
        }
    }
}
