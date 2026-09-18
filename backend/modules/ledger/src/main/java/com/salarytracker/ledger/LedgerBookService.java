package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ConflictException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.salarytracker.ledger.LedgerModels.*;

@Service
public class LedgerBookService {
    private static final Set<String> ROLE_PERMISSIONS = Set.of(
            "BOOK_DELETE", "RESOURCE_MANAGE", "MEMBER_MANAGE", "ROLE_MANAGE",
            "TRANSACTION_ANY_WRITE", "TRANSACTION_OWN_WRITE", "AUDIT_ALL_READ",
            "AUDIT_ALL_CLEAR", "AUDIT_SELF_READ", "RECYCLE_ALL", "RECYCLE_SELF",
            "IMPORT_EXPORT");
    private static final Set<String> RESOURCE_ICONS = Set.of(
            "wallet", "bank-card", "cash", "coin", "food", "transport", "home",
            "shopping", "health", "education", "entertainment", "travel", "work",
            "user", "shop", "folder", "tag", "other", "bank-boc", "bank-abc",
            "bank-icbc", "bank-ccb", "bank-cmb", "bank-bocom", "bank-psbc",
            "alipay", "wechat-pay", "bills", "gift", "service", "location",
            "goods", "ticket", "trophy", "chat");
    private static final String BALANCE_DELTA =
            "CASE WHEN t.kind IN ('INCOME','BORROW_IN','COLLECT_DEBT','TRANSFER_IN') THEN t.amount " +
                    "WHEN t.kind IN ('EXPENSE','LEND_OUT','REPAY_DEBT','TRANSFER_OUT') THEN -t.amount ELSE 0 END";
    private static final String ACCOUNT_BALANCE_DELTA =
            "CASE WHEN t.counterparty_account_id=a.id AND t.kind='TRANSFER_OUT' THEN t.amount " +
                    "WHEN t.account_id=a.id AND t.kind='TRANSFER_IN' THEN t.amount " +
                    "WHEN t.account_id=a.id AND t.kind IN ('INCOME','BORROW_IN','COLLECT_DEBT') THEN t.amount " +
                    "WHEN t.account_id=a.id AND t.kind IN ('EXPENSE','LEND_OUT','REPAY_DEBT','TRANSFER_OUT') THEN -t.amount " +
                    "ELSE 0 END";
    private static final double CATEGORY_HUE_BASE = 178;
    private static final double CATEGORY_HUE_STEP = 137.508;
    private static final Pattern HSL_COLOR = Pattern.compile(
            "hsl\\(\\s*(\\d{1,3})(?:deg)?[\\s,]+(\\d{1,3})%[\\s,]+(\\d{1,3})%\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerBookAccess access;
    private final LedgerAuditService audit;

    public LedgerBookService(JdbcTemplate jdbc,
                             ObjectMapper mapper,
                             LedgerBookAccess access,
                             LedgerAuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public List<Book> books() {
        access.ensureDefaultBook();
        long userId = access.resolve("default").userId();
        return DbRow.query(jdbc,
                "SELECT b.id,b.public_id,b.name,b.currency,b.owner_user_id,b.revision,b.archived,b.created_at," +
                        "r.code role_code,r.name role_name," +
                        "(SELECT GROUP_CONCAT(permission_code ORDER BY permission_code) FROM ledger_role_permission WHERE role_id=r.id) role_permissions," +
                        "(SELECT COUNT(*) FROM ledger_book_member all_members WHERE all_members.book_id=b.id AND all_members.deleted=FALSE) member_count," +
                        "(SELECT COUNT(*) FROM ledger_transaction all_transactions WHERE all_transactions.book_id=b.id " +
                        "AND all_transactions.deleted=FALSE AND all_transactions.kind<>'TRANSFER_IN') transaction_count " +
                        "FROM ledger_book b JOIN ledger_book_member m ON m.book_id=b.id " +
                        "JOIN ledger_role r ON r.id=m.role_id " +
                        "WHERE m.user_id=? AND m.deleted=FALSE AND b.deleted=FALSE AND r.deleted=FALSE ORDER BY b.created_at,b.id",
                userId).stream().map(this::bookView).toList();
    }

    @Transactional
    public Book createBook(BookCommand input) {
        LedgerBookAccess.Context defaultContext = access.resolve("default");
        String publicId = requestedPublicId(input == null ? null : input.id());
        String name = required(input == null ? null : input.name(), "name");
        String currency = currency(input == null ? null : input.currency());
        jdbc.update("INSERT INTO ledger_book(public_id,owner_user_id,name,currency) VALUES(?,?,?,?)",
                publicId, defaultContext.userId(), name, currency);
        long bookId = jdbc.queryForObject("SELECT id FROM ledger_book WHERE public_id=?", Long.class, publicId);
        access.seedRolesAndOwner(bookId, defaultContext.userId());

        String mode = input == null || input.mode() == null ? "" : input.mode().name();
        if ("SYSTEM_TEMPLATE".equals(mode)) {
            seedSystemTemplate(bookId, defaultContext.userId());
        } else if ("COPY".equals(mode)) {
            String sourceId = required(input.sourceBookId(), "sourceBookId");
            copyResources(access.resolve(sourceId), bookId, defaultContext.userId());
        }
        LedgerBookAccess.Context created = access.resolve(publicId);
        Book result = book(publicId, created.userId());
        audit.record(created, "book.create", "book", publicId, null, result);
        appendSync(created, UUID.randomUUID().toString(), "book", publicId, "UPSERT", result);
        return result;
    }

    @Transactional
    public Book updateBook(String bookPublicId, BookCommand input, String ifMatch) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        Book before = book(bookPublicId, context.userId());
        checkRevision(ifMatch, before.revision());
        String name = optionalText(input == null ? null : input.name(), "name", before.name());
        String currency = input != null && input.currency() != null
                ? currency(input.currency()) : before.currency();
        boolean archived = input == null || input.archived() == null ? before.archived() : input.archived();
        jdbc.update(
                "UPDATE ledger_book SET name=?,currency=?,archived=?,revision=revision+1 WHERE id=? AND deleted=FALSE",
                name, currency, archived, context.bookId());
        Book result = book(bookPublicId, context.userId());
        audit.record(context, "book.update", "book", bookPublicId, before, result);
        appendSync(context, UUID.randomUUID().toString(), "book", bookPublicId, "UPSERT", result);
        return result;
    }

    @Transactional
    public DeletedResource deleteBook(String bookPublicId, String ifMatch) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        if (!context.isOwner()) access.require(context, "BOOK_DELETE");
        Book before = book(bookPublicId, context.userId());
        checkRevision(ifMatch, before.revision());
        long activeBooks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_book_member m JOIN ledger_book b ON b.id=m.book_id " +
                        "WHERE m.user_id=? AND m.deleted=FALSE AND b.deleted=FALSE",
                Long.class, context.userId());
        if (activeBooks <= 1) throw new IllegalArgumentException("至少保留一个可用账本");
        jdbc.update(
                "UPDATE ledger_book SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 WHERE id=?",
                context.bookId());
        DeletedResource result = tombstone(bookPublicId, before.revision() + 1, null);
        audit.record(context, "book.delete", "book", bookPublicId, before, result);
        appendSync(context, UUID.randomUUID().toString(), "book", bookPublicId, "DELETE", result);
        return result;
    }

    public List<Account> accounts(String bookPublicId, boolean includeHidden) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        String hidden = includeHidden ? "" : " AND a.hidden=FALSE";
        return DbRow.query(jdbc,
                "SELECT a.public_id,a.name,a.icon,a.account_type,a.currency,a.opening_balance,a.hidden,a.revision,a.created_at," +
                        "a.opening_balance+COALESCE(SUM(" + ACCOUNT_BALANCE_DELTA + "),0) balance " +
                        "FROM ledger_account a LEFT JOIN ledger_transaction t ON t.book_id=a.book_id AND t.deleted=FALSE " +
                        "AND ((t.account_id=a.id AND t.kind<>'TRANSFER_IN') OR (t.counterparty_account_id=a.id AND t.kind='TRANSFER_OUT') " +
                        "OR (t.account_id=a.id AND t.kind='TRANSFER_IN' AND NOT EXISTS " +
                        "(SELECT 1 FROM ledger_transaction source_transfer WHERE source_transfer.transfer_group_id=t.transfer_group_id " +
                        "AND source_transfer.kind='TRANSFER_OUT' AND source_transfer.book_id=t.book_id AND source_transfer.deleted=FALSE))) " +
                        "WHERE a.book_id=? AND a.deleted=FALSE" + hidden +
                        " GROUP BY a.id ORDER BY a.created_at,a.id",
                context.bookId()).stream().map(this::accountView).toList();
    }

    @Transactional
    public Account createAccount(String bookPublicId, AccountCommand input, String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        String publicId = requestedPublicId(input == null ? null : input.id());
        jdbc.update(
                "INSERT INTO ledger_account(public_id,user_id,book_id,name,icon,account_type,currency,opening_balance,hidden,created_by) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?)",
                publicId, context.userId(), context.bookId(), required(input == null ? null : input.name(), "name"),
                icon(input == null ? null : input.icon(), "wallet"),
                accountType(input == null ? null : input.accountType()), currency(input == null ? null : input.currency()),
                amount(input == null || input.openingBalance() == null ? 0 : input.openingBalance()),
                input != null && Boolean.TRUE.equals(input.hidden()),
                context.userId());
        Account result = account(context, publicId, true);
        changed(context, opId, "account.create", "account", publicId, "UPSERT", null, result);
        return result;
    }

    @Transactional
    public Account updateAccount(String bookPublicId,
                                             String resourceId,
                                             AccountCommand input,
                                             String ifMatch,
                                             String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        Account before = account(context, resourceId, true);
        checkRevision(ifMatch, before.revision());
        jdbc.update(
                "UPDATE ledger_account SET name=?,icon=?,account_type=?,currency=?,opening_balance=?,hidden=?,revision=revision+1 " +
                        "WHERE public_id=? AND book_id=? AND deleted=FALSE",
                optionalText(input == null ? null : input.name(), "name", before.name()),
                input != null && input.icon() != null ? icon(input.icon(), "wallet") : before.icon(),
                input != null && input.accountType() != null ? accountType(input.accountType()) : before.accountType(),
                input != null && input.currency() != null ? currency(input.currency()) : before.currency(),
                input != null && input.openingBalance() != null ? amount(input.openingBalance()) : before.openingBalance(),
                input != null && input.hidden() != null ? input.hidden() : before.hidden(),
                resourceId, context.bookId());
        Account result = account(context, resourceId, true);
        changed(context, opId, "account.update", "account", resourceId, "UPSERT", before, result);
        return result;
    }

    @Transactional
    public DeletedResource deleteAccount(String bookPublicId,
                                             String resourceId,
                                             String ifMatch,
                                             String opId) {
        return softDeleteResource(bookPublicId, "account", resourceId, ifMatch, opId);
    }

    public List<Category> categories(String bookPublicId, boolean includeHidden) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        String hidden = includeHidden ? "" : " AND c.hidden=FALSE";
        return DbRow.query(jdbc,
                "SELECT c.public_id,c.name,c.icon,c.kind,c.color,c.hidden,c.revision,c.created_at,parent.public_id parent_public_id " +
                        "FROM ledger_category c LEFT JOIN ledger_category parent ON parent.id=c.parent_id " +
                        "WHERE c.book_id=? AND c.deleted=FALSE" + hidden + " ORDER BY c.kind,c.parent_id,c.name",
                context.bookId()).stream().map(this::categoryView).toList();
    }

    @Transactional
    public Category createCategory(String bookPublicId, CategoryCommand input, String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        String publicId = requestedPublicId(input == null ? null : input.id());
        String kind = categoryKind(input == null ? null : input.kind());
        Long parentId = internalId(context, "ledger_category", text(input == null ? null : input.parentId()), false);
        DbRow parent = null;
        if (parentId != null) {
            parent = DbRow.one(jdbc,
                    "SELECT public_id,name,kind,parent_id,color FROM ledger_category WHERE id=? AND book_id=? AND deleted=FALSE",
                    parentId, context.bookId());
            if (parent.get("parent_id") != null) throw new IllegalArgumentException("分类最多支持两级");
            if (!kind.equals(parent.get("kind"))) throw new IllegalArgumentException("父子分类类型必须一致");
        }
        jdbc.update(
                "INSERT INTO ledger_category(public_id,user_id,book_id,name,icon,kind,parent_id,color,hidden,created_by) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?)",
                publicId, context.userId(), context.bookId(), required(input == null ? null : input.name(), "name"),
                icon(input == null ? null : input.icon(), "tag"), kind, parentId,
                categoryInputColor(input, context.bookId(), kind, parentId, parent),
                input != null && Boolean.TRUE.equals(input.hidden()),
                context.userId());
        Category result = category(context, publicId, true);
        changed(context, opId, "category.create", "category", publicId, "UPSERT", null, result);
        return result;
    }

    @Transactional
    public Category updateCategory(String bookPublicId,
                                              String resourceId,
                                              CategoryCommand input,
                                              String ifMatch,
                                              String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        Category before = category(context, resourceId, true);
        checkRevision(ifMatch, before.revision());
        String kind = input != null && input.kind() != null ? categoryKind(input.kind()) : before.kind().name();
        String parentPublicId = input != null && input.parentId() != null ? text(input.parentId()) : text(before.parentId());
        Long parentId = internalId(context, "ledger_category", parentPublicId, false);
        if (resourceId.equals(parentPublicId)) throw new IllegalArgumentException("分类不能作为自己的父级");
        if (parentId != null) {
            DbRow parent = DbRow.one(jdbc,
                    "SELECT kind,parent_id FROM ledger_category WHERE id=? AND book_id=? AND deleted=FALSE",
                    parentId, context.bookId());
            if (parent.get("parent_id") != null) throw new IllegalArgumentException("分类最多支持两级");
            if (!kind.equals(parent.get("kind"))) throw new IllegalArgumentException("父子分类类型必须一致");
        }
        jdbc.update(
                "UPDATE ledger_category SET name=?,icon=?,kind=?,parent_id=?,color=?,hidden=?,revision=revision+1 " +
                        "WHERE public_id=? AND book_id=? AND deleted=FALSE",
                optionalText(input == null ? null : input.name(), "name", before.name()),
                input != null && input.icon() != null ? icon(input.icon(), "tag") : before.icon(),
                kind, parentId,
                input != null && input.color() != null ? textOr(input.color(), "#0f5132") : before.color(),
                input != null && input.hidden() != null ? input.hidden() : before.hidden(),
                resourceId, context.bookId());
        Category result = category(context, resourceId, true);
        changed(context, opId, "category.update", "category", resourceId, "UPSERT", before, result);
        return result;
    }

    @Transactional
    public DeletedResource deleteCategory(String bookPublicId,
                                              String resourceId,
                                              String ifMatch,
                                              String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        Long id = internalId(context, "ledger_category", resourceId, true);
        if (jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_category WHERE parent_id=? AND deleted=FALSE",
                Long.class, id) > 0) {
            throw new IllegalArgumentException("请先删除或移动该分类下的二级分类");
        }
        return softDeleteResource(bookPublicId, "category", resourceId, ifMatch, opId);
    }

    public List<NamedResource> merchants(String bookPublicId, boolean includeHidden) {
        return namedResources(access.resolve(bookPublicId), "merchant", includeHidden);
    }

    public List<NamedResource> projects(String bookPublicId, boolean includeHidden) {
        return namedResources(access.resolve(bookPublicId), "project", includeHidden);
    }

    @Transactional
    public NamedResource createNamedResource(String bookPublicId,
                                                   String type,
                                                   NamedResourceCommand input,
                                                   String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        ResourceTable table = resourceTable(type);
        String publicId = requestedPublicId(input == null ? null : input.id());
        if ("project".equals(type)) {
            jdbc.update(
                    "INSERT INTO ledger_project(public_id,book_id,name,icon,color,note,hidden,created_by) VALUES(?,?,?,?,?,?,?,?)",
                    publicId, context.bookId(), required(input == null ? null : input.name(), "name"),
                    icon(input == null ? null : input.icon(), "folder"),
                    textOr(input == null ? null : input.color(), "#0f5132"), text(input == null ? null : input.note()),
                    input != null && Boolean.TRUE.equals(input.hidden()), context.userId());
        } else {
            jdbc.update(
                    "INSERT INTO ledger_merchant(public_id,book_id,name,icon,note,hidden,created_by) VALUES(?,?,?,?,?,?,?)",
                    publicId, context.bookId(), required(input == null ? null : input.name(), "name"),
                    icon(input == null ? null : input.icon(), "shop"), text(input == null ? null : input.note()),
                    input != null && Boolean.TRUE.equals(input.hidden()), context.userId());
        }
        NamedResource result = namedResource(context, table, publicId, true);
        changed(context, opId, type + ".create", type, publicId, "UPSERT", null, result);
        return result;
    }

    @Transactional
    public NamedResource updateNamedResource(String bookPublicId,
                                                   String type,
                                                   String resourceId,
                                                   NamedResourceCommand input,
                                                   String ifMatch,
                                                   String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        ResourceTable table = resourceTable(type);
        NamedResource before = namedResource(context, table, resourceId, true);
        checkRevision(ifMatch, before.revision());
        if ("project".equals(type)) {
            jdbc.update(
                    "UPDATE ledger_project SET name=?,icon=?,color=?,note=?,hidden=?,revision=revision+1 " +
                            "WHERE public_id=? AND book_id=? AND deleted=FALSE",
                    optionalText(input == null ? null : input.name(), "name", before.name()),
                    input != null && input.icon() != null ? icon(input.icon(), "folder") : before.icon(),
                    input != null && input.color() != null ? textOr(input.color(), "#0f5132") : before.color(),
                    input != null && input.note() != null ? text(input.note()) : before.note(),
                    input != null && input.hidden() != null ? input.hidden() : before.hidden(),
                    resourceId, context.bookId());
        } else {
            jdbc.update(
                    "UPDATE ledger_merchant SET name=?,icon=?,note=?,hidden=?,revision=revision+1 " +
                            "WHERE public_id=? AND book_id=? AND deleted=FALSE",
                    optionalText(input == null ? null : input.name(), "name", before.name()),
                    input != null && input.icon() != null ? icon(input.icon(), "shop") : before.icon(),
                    input != null && input.note() != null ? text(input.note()) : before.note(),
                    input != null && input.hidden() != null ? input.hidden() : before.hidden(),
                    resourceId, context.bookId());
        }
        NamedResource result = namedResource(context, table, resourceId, true);
        changed(context, opId, type + ".update", type, resourceId, "UPSERT", before, result);
        return result;
    }

    @Transactional
    public DeletedResource deleteNamedResource(String bookPublicId,
                                                   String type,
                                                   String resourceId,
                                                   String ifMatch,
                                                   String opId) {
        return softDeleteResource(bookPublicId, type, resourceId, ifMatch, opId);
    }

    public List<Member> members(String bookPublicId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        return DbRow.query(jdbc,
                "SELECT m.public_id,m.user_id,m.created_by,m.icon,u.username,u.nickname,r.public_id role_public_id,r.code role_code,r.name role_name," +
                        "m.revision,m.created_at FROM ledger_book_member m JOIN app_user u ON u.id=m.user_id " +
                        "JOIN ledger_role r ON r.id=m.role_id WHERE m.book_id=? AND m.deleted=FALSE ORDER BY m.created_at,m.id",
                context.bookId()).stream().map(this::memberView).toList();
    }

    @Transactional
    public Member addMember(String bookPublicId, MemberCommand input, String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "MEMBER_MANAGE");
        String username = required(input == null ? null : input.username(), "username");
        List<DbRow> users = DbRow.query(jdbc,
                "SELECT id FROM app_user WHERE username=? AND status='ACTIVE' FOR UPDATE", username);
        if (users.isEmpty()) throw new IllegalArgumentException("该用户名未注册或账号不可用，请先确认用户名");
        long userId = number(users.get(0).get("id"));
        Integer existingActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_book_member WHERE book_id=? AND user_id=? AND deleted=FALSE",
                Integer.class, context.bookId(), userId);
        if (existingActive != null && existingActive > 0) {
            throw new IllegalArgumentException("该用户已是当前账本成员");
        }
        String rolePublicId = text(input == null ? null : input.roleId());
        Long roleId = rolePublicId.isBlank()
                ? jdbc.queryForObject("SELECT id FROM ledger_role WHERE book_id=? AND code='MEMBER'", Long.class, context.bookId())
                : internalId(context, "ledger_role", rolePublicId, true);
        String memberPublicId = requestedPublicId(input == null ? null : input.id());
        try {
            jdbc.update(
                    "INSERT INTO ledger_book_member(public_id,book_id,user_id,role_id,icon,created_by) VALUES(?,?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE role_id=VALUES(role_id),icon=VALUES(icon),deleted=FALSE,deleted_at=NULL,revision=revision+1",
                    memberPublicId, context.bookId(), userId, roleId,
                    icon(input == null ? null : input.icon(), "user"), context.userId());
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该用户已是当前账本成员，请刷新后重试", exception);
        }
        Member result = memberByUser(context, userId);
        changed(context, opId, "member.add", "member", result.id(), "UPSERT", null, result);
        return result;
    }

    @Transactional
    public Member updateMember(String bookPublicId,
                                            String memberPublicId,
                                            MemberCommand input,
                                            String ifMatch,
                                            String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "MEMBER_MANAGE");
        Member before = member(context, memberPublicId, true);
        checkRevision(ifMatch, before.revision());
        String rolePublicId = input != null && input.roleId() != null
                ? required(input.roleId(), "roleId") : before.roleId();
        long roleId = internalId(context, "ledger_role", rolePublicId, true);
        if ("OWNER".equals(before.roleCode()) && !rolePublicId.equals(before.roleId())) {
            throw new IllegalArgumentException("不能修改账本主人的角色");
        }
        String roleCode = jdbc.queryForObject(
                "SELECT code FROM ledger_role WHERE id=? AND book_id=? AND deleted=FALSE",
                String.class, roleId, context.bookId());
        if ("OWNER".equals(roleCode) && !"OWNER".equals(before.roleCode())) {
            throw new IllegalArgumentException("不能通过成员编辑转移账本所有权");
        }
        jdbc.update(
                "UPDATE ledger_book_member SET role_id=?,icon=?,revision=revision+1 WHERE public_id=? AND book_id=? AND deleted=FALSE",
                roleId,
                input != null && input.icon() != null ? icon(input.icon(), "user") : before.icon(),
                memberPublicId, context.bookId());
        Member result = member(context, memberPublicId, true);
        changed(context, opId, "member.update", "member", memberPublicId, "UPSERT", before, result);
        return result;
    }

    @Transactional
    public DeletedResource deleteMember(String bookPublicId,
                                            String memberPublicId,
                                            String ifMatch,
                                            String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "MEMBER_MANAGE");
        Member before = member(context, memberPublicId, true);
        checkRevision(ifMatch, before.revision());
        if ("OWNER".equals(before.roleCode())) throw new IllegalArgumentException("不能移除账本主人");
        jdbc.update(
                "UPDATE ledger_book_member SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 " +
                        "WHERE public_id=? AND book_id=?",
                memberPublicId, context.bookId());
        DeletedResource result = tombstone(memberPublicId, before.revision() + 1, before.createdBy());
        changed(context, opId, "member.delete", "member", memberPublicId, "DELETE", before, result);
        return result;
    }

    public List<Role> roles(String bookPublicId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        return DbRow.query(jdbc,
                "SELECT r.public_id,r.code,r.name,r.system_role,r.created_by,r.revision,r.created_at," +
                        "GROUP_CONCAT(rp.permission_code ORDER BY rp.permission_code SEPARATOR ',') permissions " +
                        "FROM ledger_role r LEFT JOIN ledger_role_permission rp ON rp.role_id=r.id " +
                        "WHERE r.book_id=? AND r.deleted=FALSE GROUP BY r.id ORDER BY r.system_role DESC,r.id",
                context.bookId()).stream().map(this::roleView).toList();
    }

    @Transactional
    public Role createRole(String bookPublicId, RoleCommand input, String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "ROLE_MANAGE");
        String publicId = requestedPublicId(input == null ? null : input.id());
        String code = "CUSTOM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        jdbc.update(
                "INSERT INTO ledger_role(public_id,book_id,code,name,system_role,created_by) VALUES(?,?,?,?,FALSE,?)",
                publicId, context.bookId(), code, required(input == null ? null : input.name(), "name"), context.userId());
        long roleId = jdbc.queryForObject("SELECT id FROM ledger_role WHERE public_id=?", Long.class, publicId);
        replacePermissions(roleId, input == null ? null : input.permissions());
        Role result = role(context, publicId, true);
        changed(context, opId, "role.create", "role", publicId, "UPSERT", null, result);
        return result;
    }

    @Transactional
    public Role updateRole(String bookPublicId,
                                          String rolePublicId,
                                          RoleCommand input,
                                          String ifMatch,
                                          String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "ROLE_MANAGE");
        Role before = role(context, rolePublicId, true);
        checkRevision(ifMatch, before.revision());
        if (before.systemRole()) throw new IllegalArgumentException("系统角色不可编辑");
        jdbc.update(
                "UPDATE ledger_role SET name=?,revision=revision+1 WHERE public_id=? AND book_id=? AND deleted=FALSE",
                optionalText(input == null ? null : input.name(), "name", before.name()),
                rolePublicId, context.bookId());
        if (input != null && input.permissions() != null) {
            long roleId = internalId(context, "ledger_role", rolePublicId, true);
            replacePermissions(roleId, input.permissions());
        }
        Role result = role(context, rolePublicId, true);
        changed(context, opId, "role.update", "role", rolePublicId, "UPSERT", before, result);
        return result;
    }

    @Transactional
    public DeletedResource deleteRole(String bookPublicId,
                                          String rolePublicId,
                                          String ifMatch,
                                          String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "ROLE_MANAGE");
        Role before = role(context, rolePublicId, true);
        checkRevision(ifMatch, before.revision());
        if (before.systemRole()) throw new IllegalArgumentException("系统角色不可删除");
        long roleId = internalId(context, "ledger_role", rolePublicId, true);
        long members = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_book_member WHERE role_id=? AND deleted=FALSE", Long.class, roleId);
        if (members > 0) throw new IllegalArgumentException("请先为使用该角色的成员更换角色");
        jdbc.update(
                "UPDATE ledger_role SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 WHERE id=?",
                roleId);
        DeletedResource result = tombstone(rolePublicId, before.revision() + 1, before.createdBy());
        changed(context, opId, "role.delete", "role", rolePublicId, "DELETE", before, result);
        return result;
    }

    public List<Budget> budgets(String bookPublicId, String month) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        String monthKey = month == null || month.isBlank() ? YearMonth.now().toString() : YearMonth.parse(month).toString();
        return DbRow.query(jdbc,
                "SELECT b.public_id,b.month_key,b.amount budget,b.revision,b.category_id,c.public_id category_public_id,c.name category," +
                        "COALESCE((SELECT SUM(t.amount) FROM ledger_transaction t WHERE t.book_id=b.book_id " +
                        "AND t.kind='EXPENSE' AND t.deleted=FALSE AND DATE_FORMAT(t.occurred_on,'%Y-%m')=b.month_key " +
                        "AND (b.category_id IS NULL OR t.category_id=b.category_id OR EXISTS " +
                        "(SELECT 1 FROM ledger_category tc WHERE tc.id=t.category_id AND tc.parent_id=b.category_id))),0) spent " +
                        "FROM ledger_budget b LEFT JOIN ledger_category c ON c.id=b.category_id " +
                        "WHERE b.book_id=? AND b.month_key=? AND b.deleted=FALSE ORDER BY b.category_id IS NOT NULL,c.name",
                context.bookId(), monthKey).stream().map(this::budgetView).toList();
    }

    @Transactional
    public Budget upsertBudget(String bookPublicId, BudgetCommand input, String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        String month = budgetMonth(input);
        Object categoryInput = input == null ? null : input.categoryId();
        Long categoryId = budgetCategoryId(context, categoryInput);
        BigDecimal budgetAmount = positiveBudgetAmount(input == null ? null : input.budget());
        String publicId = requestedPublicId(input == null ? null : input.id());
        List<DbRow> existing = DbRow.query(jdbc,
                "SELECT public_id FROM ledger_budget WHERE book_id=? AND month_key=? " +
                        "AND ((category_id IS NULL AND ? IS NULL) OR category_id=?)",
                context.bookId(), month, categoryId, categoryId);
        Budget before = null;
        if (existing.isEmpty()) {
            jdbc.update(
                    "INSERT INTO ledger_budget(public_id,user_id,book_id,category_id,month_key,amount,created_by) " +
                            "VALUES(?,?,?,?,?,?,?)",
                    publicId, context.userId(), context.bookId(), categoryId, month,
                    budgetAmount, context.userId());
        } else {
            publicId = String.valueOf(existing.get(0).get("public_id"));
            before = budget(context, publicId, true);
            jdbc.update(
                    "UPDATE ledger_budget SET amount=?,deleted=FALSE,deleted_at=NULL,revision=revision+1 " +
                            "WHERE public_id=? AND book_id=?",
                    budgetAmount, publicId, context.bookId());
        }
        Budget result = budget(context, publicId, true);
        changed(context, opId, before == null ? "budget.create" : "budget.update",
                "budget", publicId, "UPSERT", before, result);
        return result;
    }

    String budgetMonth(BudgetCommand input) {
        String value = required(input == null ? null : input.monthKey(), "monthKey");
        try {
            return YearMonth.parse(value).toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("monthKey 格式不正确，应为 YYYY-MM");
        }
    }

    BigDecimal positiveBudgetAmount(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("amount 必填");
        }
        BigDecimal result = amount(value);
        if (result.signum() <= 0) throw new IllegalArgumentException("预算金额必须大于 0");
        return result;
    }

    Long budgetCategoryId(LedgerBookAccess.Context context, Object value) {
        String publicId = text(value);
        if (publicId.isBlank() || "0".equals(publicId)) return null;
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT id,kind,hidden FROM ledger_category " +
                        "WHERE public_id=? AND book_id=? AND deleted=FALSE",
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("分类不存在");
        DbRow category = rows.get(0);
        if (!"EXPENSE".equals(String.valueOf(category.get("kind")))) {
            throw new IllegalArgumentException("预算只能关联支出分类");
        }
        Object hidden = category.get("hidden");
        if (Boolean.TRUE.equals(hidden) || (hidden instanceof Number number && number.intValue() != 0)) {
            throw new IllegalArgumentException("隐藏分类不能设置预算");
        }
        return number(category.get("id"));
    }

    @Transactional
    public DeletedResource deleteBudget(String bookPublicId,
                                            String budgetPublicId,
                                            String ifMatch,
                                            String opId) {
        return softDeleteResource(bookPublicId, "budget", budgetPublicId, ifMatch, opId);
    }

    @Transactional
    public ResourceView restoreResource(String bookPublicId, String type, String resourceId, String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        if (!context.isAdmin()) access.require(context, "RECYCLE_SELF");
        ResourceTable table = resourceTable(type);
        DeletedResource before = deletedResource(context, table, resourceId);
        long createdBy = before.createdBy();
        if (!context.isAdmin() && createdBy != context.userId()) {
            throw new com.salarytracker.platform.ForbiddenException("无权恢复他人删除的数据");
        }
        jdbc.update(
                "UPDATE " + table.table() + " SET deleted=FALSE,deleted_at=NULL,revision=revision+1 " +
                        "WHERE public_id=? AND book_id=?",
                resourceId, context.bookId());
        ResourceView result = activeResource(context, table, resourceId);
        changed(context, opId, type + ".restore", type, resourceId, "UPSERT", before, result);
        return result;
    }

    public RecyclePage recycle(String bookPublicId, int page, int pageSize) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        boolean all = context.isAdmin() || context.permissions().contains("RECYCLE_ALL");
        if (!all) access.require(context, "RECYCLE_SELF");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        List<RecycleItem> items = new ArrayList<>();
        addDeleted(items, context, "account", "ledger_account", "name", all);
        addDeleted(items, context, "category", "ledger_category", "name", all);
        addDeleted(items, context, "merchant", "ledger_merchant", "name", all);
        addDeleted(items, context, "project", "ledger_project", "name", all);
        addDeleted(items, context, "budget", "ledger_budget", "month_key", all);
        addDeleted(items, context, "role", "ledger_role", "name", all);
        addDeleted(items, context, "member", "ledger_book_member", "public_id", all);
        addDeletedTransactions(items, context, all);
        items.sort((left, right) -> String.valueOf(right.deletedAt())
                .compareTo(String.valueOf(left.deletedAt())));
        int from = Math.min((safePage - 1) * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        return new RecyclePage(items.subList(from, to), safePage, safeSize, items.size(),
                Math.max(1, (items.size() + safeSize - 1L) / safeSize));
    }

    @Transactional
    public DeletedResource purgeResource(String bookPublicId, String type, String resourceId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        if (!context.isAdmin()) throw new com.salarytracker.platform.ForbiddenException("只有主人或管理员可以永久删除");
        ResourceTable table = resourceTable(type);
        DeletedResource before = deletedResource(context, table, resourceId);
        if (isReferenced(table, context.bookId(), internalIdAny(context, table, resourceId))) {
            if ("member".equals(type) || "role".equals(type)) {
                throw new IllegalArgumentException("该资源仍被历史数据引用，不能永久删除");
            }
            jdbc.update(
                    "UPDATE " + table.table() + " SET name='已删除',revision=revision+1 WHERE public_id=? AND book_id=?",
                    resourceId, context.bookId());
            DeletedResource result = new DeletedResource(resourceId, before.revision() + 1,
                    true, before.deletedAt(), before.createdBy(), true, null);
            audit.record(context, type + ".anonymize", type, resourceId, before, result);
            return result;
        }
        if ("role".equals(type)) {
            long roleId = internalIdAny(context, table, resourceId);
            jdbc.update("DELETE FROM ledger_role_permission WHERE role_id=?", roleId);
        }
        jdbc.update("DELETE FROM " + table.table() + " WHERE public_id=? AND book_id=? AND deleted=TRUE",
                resourceId, context.bookId());
        audit.record(context, type + ".purge", type, resourceId, before, null);
        return new DeletedResource(resourceId, before.revision(), true,
                before.deletedAt(), before.createdBy(), null, true);
    }

    Account account(LedgerBookAccess.Context context, String publicId, boolean includeHidden) {
        String hidden = includeHidden ? "" : " AND a.hidden=FALSE";
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT a.public_id,a.name,a.icon,a.account_type,a.currency,a.opening_balance,a.hidden,a.revision,a.created_at," +
                        "a.opening_balance+COALESCE((SELECT SUM(" + ACCOUNT_BALANCE_DELTA + ") FROM ledger_transaction t " +
                        "WHERE t.book_id=a.book_id AND t.deleted=FALSE " +
                        "AND ((t.account_id=a.id AND t.kind<>'TRANSFER_IN') OR (t.counterparty_account_id=a.id AND t.kind='TRANSFER_OUT') " +
                        "OR (t.account_id=a.id AND t.kind='TRANSFER_IN' AND NOT EXISTS " +
                        "(SELECT 1 FROM ledger_transaction source_transfer WHERE source_transfer.transfer_group_id=t.transfer_group_id " +
                        "AND source_transfer.kind='TRANSFER_OUT' AND source_transfer.book_id=t.book_id AND source_transfer.deleted=FALSE)))),0) balance " +
                        "FROM ledger_account a WHERE a.public_id=? AND a.book_id=? AND a.deleted=FALSE" + hidden,
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("账户不存在");
        return accountView(rows.get(0));
    }

    Category category(LedgerBookAccess.Context context, String publicId, boolean includeHidden) {
        String hidden = includeHidden ? "" : " AND c.hidden=FALSE";
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT c.public_id,c.name,c.icon,c.kind,c.color,c.hidden,c.revision,c.created_at,parent.public_id parent_public_id " +
                        "FROM ledger_category c LEFT JOIN ledger_category parent ON parent.id=c.parent_id " +
                        "WHERE c.public_id=? AND c.book_id=? AND c.deleted=FALSE" + hidden,
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("分类不存在");
        return categoryView(rows.get(0));
    }

    Long internalId(LedgerBookAccess.Context context, String table, String publicId, boolean required) {
        if (publicId == null || publicId.isBlank()) {
            if (required) throw new IllegalArgumentException("资源 ID 必填");
            return null;
        }
        ResourceTable mapped = table.startsWith("ledger_") ? resourceTableByName(table) : resourceTable(table);
        List<Long> rows = DbRow.query(jdbc,
                "SELECT id FROM " + mapped.table() + " WHERE public_id=? AND book_id=? AND deleted=FALSE",
                Long.class, publicId, context.bookId());
        if (rows.isEmpty()) {
            if (required) throw new IllegalArgumentException(mapped.label() + "不存在");
            return null;
        }
        return rows.get(0);
    }

    private long internalIdAny(LedgerBookAccess.Context context, ResourceTable table, String publicId) {
        List<Long> rows = DbRow.query(jdbc,
                "SELECT id FROM " + table.table() + " WHERE public_id=? AND book_id=?",
                Long.class, publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException(table.label() + "不存在");
        return rows.get(0);
    }

    private void changed(LedgerBookAccess.Context context,
                         String opId,
                         String auditAction,
                         String entity,
                         String entityId,
                         String operation,
                         Object before,
                         Object after) {
        audit.record(context, auditAction, entity, entityId, before, after);
        appendSync(context, opId, entity, entityId, operation, after);
    }

    void appendSync(LedgerBookAccess.Context context,
                    String opId,
                    String entity,
                    String entityId,
                    String operation,
                    Object payload) {
        String id = opId == null || opId.isBlank() ? UUID.randomUUID().toString() : opId;
        if (id.length() > 160) id = id.substring(0, 160);
        jdbc.update(
                "INSERT IGNORE INTO ledger_sync_oplog(user_id,book_id,actor_user_id,op_id,entity_type,entity_id,operation,payload_json) " +
                        "VALUES(?,?,?,?,?,?,?,?)",
                context.userId(), context.bookId(), context.userId(), id, entity, entityId,
                operation, json(payload));
    }

    private DeletedResource softDeleteResource(String bookPublicId,
                                                   String type,
                                                   String resourceId,
                                                   String ifMatch,
                                                   String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        ResourceTable table = resourceTable(type);
        ResourceView before = activeResource(context, table, resourceId);
        long revision = resourceRevision(before);
        checkRevision(ifMatch, revision);
        jdbc.update(
                "UPDATE " + table.table() + " SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 " +
                        "WHERE public_id=? AND book_id=? AND deleted=FALSE",
                resourceId, context.bookId());
        Long createdBy = resourceCreatedBy(before);
        DeletedResource result = tombstone(resourceId, revision + 1, createdBy);
        changed(context, opId, type + ".delete", type, resourceId, "DELETE", before, result);
        return result;
    }

    private ResourceView activeResource(LedgerBookAccess.Context context,
                                               ResourceTable table,
                                               String publicId) {
        return switch (table.type()) {
            case "account" -> account(context, publicId, true);
            case "category" -> category(context, publicId, true);
            case "merchant", "project" -> namedResource(context, table, publicId, true);
            case "budget" -> budget(context, publicId, true);
            case "member" -> member(context, publicId, true);
            case "role" -> role(context, publicId, true);
            default -> throw new IllegalArgumentException("不支持的资源类型");
        };
    }

    private long resourceRevision(ResourceView resource) {
        if (resource instanceof Account value) return value.revision();
        if (resource instanceof Category value) return value.revision();
        if (resource instanceof NamedResource value) return value.revision();
        if (resource instanceof Member value) return value.revision();
        if (resource instanceof Role value) return value.revision();
        if (resource instanceof Budget value) return value.revision();
        throw new IllegalArgumentException("不支持的资源类型");
    }

    private Long resourceCreatedBy(ResourceView resource) {
        if (resource instanceof Member value) return value.createdBy();
        if (resource instanceof Role value) return value.createdBy();
        return null;
    }

    private DeletedResource deletedResource(LedgerBookAccess.Context context,
                                                ResourceTable table,
                                                String publicId) {
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT public_id,revision,created_by,deleted_at FROM " + table.table() +
                        " WHERE public_id=? AND book_id=? AND deleted=TRUE",
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("回收站中不存在该资源");
        DbRow row = rows.get(0);
        return new DeletedResource(String.valueOf(row.get("public_id")), number(row.get("revision")),
                true, text(row.get("deleted_at")), number(row.get("created_by")), null, null);
    }

    private List<NamedResource> namedResources(LedgerBookAccess.Context context,
                                                     String type,
                                                     boolean includeHidden) {
        ResourceTable table = resourceTable(type);
        String hidden = includeHidden ? "" : " AND hidden=FALSE";
        return DbRow.query(jdbc,
                "SELECT public_id,name,icon,note,hidden,revision,created_at" +
                        ("project".equals(type) ? ",color" : "") +
                        " FROM " + table.table() + " WHERE book_id=? AND deleted=FALSE" + hidden +
                        " ORDER BY name",
                context.bookId()).stream().map(row -> namedView(type, row)).toList();
    }

    private NamedResource namedResource(LedgerBookAccess.Context context,
                                              ResourceTable table,
                                              String publicId,
                                              boolean includeHidden) {
        String hidden = includeHidden ? "" : " AND hidden=FALSE";
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT public_id,name,icon,note,hidden,revision,created_at" +
                        ("project".equals(table.type()) ? ",color" : "") +
                        " FROM " + table.table() + " WHERE public_id=? AND book_id=? AND deleted=FALSE" + hidden,
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException(table.label() + "不存在");
        return namedView(table.type(), rows.get(0));
    }

    private Member member(LedgerBookAccess.Context context,
                                       String publicId,
                                       boolean activeOnly) {
        String deleted = activeOnly ? " AND m.deleted=FALSE" : "";
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT m.public_id,m.user_id,m.created_by,m.icon,u.username,u.nickname,r.public_id role_public_id,r.code role_code,r.name role_name," +
                        "m.revision,m.created_at FROM ledger_book_member m JOIN app_user u ON u.id=m.user_id " +
                        "JOIN ledger_role r ON r.id=m.role_id WHERE m.public_id=? AND m.book_id=?" + deleted,
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("成员不存在");
        return memberView(rows.get(0));
    }

    private Member memberByUser(LedgerBookAccess.Context context, long userId) {
        DbRow row = DbRow.one(jdbc,
                "SELECT m.public_id,m.user_id,m.created_by,m.icon,u.username,u.nickname,r.public_id role_public_id,r.code role_code,r.name role_name," +
                        "m.revision,m.created_at FROM ledger_book_member m JOIN app_user u ON u.id=m.user_id " +
                        "JOIN ledger_role r ON r.id=m.role_id WHERE m.user_id=? AND m.book_id=? AND m.deleted=FALSE",
                userId, context.bookId());
        return memberView(row);
    }

    private Role role(LedgerBookAccess.Context context,
                                     String publicId,
                                     boolean activeOnly) {
        String deleted = activeOnly ? " AND r.deleted=FALSE" : "";
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT r.public_id,r.code,r.name,r.system_role,r.created_by,r.revision,r.created_at," +
                        "GROUP_CONCAT(rp.permission_code ORDER BY rp.permission_code SEPARATOR ',') permissions " +
                        "FROM ledger_role r LEFT JOIN ledger_role_permission rp ON rp.role_id=r.id " +
                        "WHERE r.public_id=? AND r.book_id=?" + deleted + " GROUP BY r.id",
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("角色不存在");
        return roleView(rows.get(0));
    }

    private Budget budget(LedgerBookAccess.Context context,
                                       String publicId,
                                       boolean activeOnly) {
        String deleted = activeOnly ? " AND b.deleted=FALSE" : "";
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT b.public_id,b.month_key,b.amount budget,b.revision,b.category_id,c.public_id category_public_id,c.name category," +
                        "COALESCE((SELECT SUM(t.amount) FROM ledger_transaction t WHERE t.book_id=b.book_id " +
                        "AND t.kind='EXPENSE' AND t.deleted=FALSE AND DATE_FORMAT(t.occurred_on,'%Y-%m')=b.month_key " +
                        "AND (b.category_id IS NULL OR t.category_id=b.category_id OR EXISTS " +
                        "(SELECT 1 FROM ledger_category tc WHERE tc.id=t.category_id AND tc.parent_id=b.category_id))),0) spent " +
                        "FROM ledger_budget b LEFT JOIN ledger_category c ON c.id=b.category_id " +
                        "WHERE b.public_id=? AND b.book_id=?" + deleted,
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("预算不存在");
        return budgetView(rows.get(0));
    }

    private Book book(String publicId, long userId) {
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT b.public_id,b.name,b.currency,b.owner_user_id,b.revision,b.archived,b.created_at," +
                        "r.code role_code,r.name role_name," +
                        "(SELECT GROUP_CONCAT(permission_code ORDER BY permission_code) FROM ledger_role_permission WHERE role_id=r.id) role_permissions," +
                        "(SELECT COUNT(*) FROM ledger_book_member all_members WHERE all_members.book_id=b.id AND all_members.deleted=FALSE) member_count," +
                        "(SELECT COUNT(*) FROM ledger_transaction all_transactions WHERE all_transactions.book_id=b.id " +
                        "AND all_transactions.deleted=FALSE AND all_transactions.kind<>'TRANSFER_IN') transaction_count " +
                        "FROM ledger_book b JOIN ledger_book_member m ON m.book_id=b.id " +
                        "JOIN ledger_role r ON r.id=m.role_id " +
                        "WHERE b.public_id=? AND m.user_id=? AND m.deleted=FALSE AND b.deleted=FALSE AND r.deleted=FALSE",
                publicId, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("账本不存在");
        return bookView(rows.get(0));
    }

    private void seedSystemTemplate(long bookId, long userId) {
        for (Object[] account : List.of(
                new Object[]{"现金", "cash"},
                new Object[]{"银行卡", "bank"},
                new Object[]{"支付宝", "wallet"},
                new Object[]{"微信钱包", "wallet"})) {
            jdbc.update(
                    "INSERT INTO ledger_account(public_id,user_id,book_id,name,account_type,created_by) VALUES(?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), userId, bookId, account[0], account[1], userId);
        }
        seedCategory(bookId, userId, "日常支出", "EXPENSE", List.of("餐饮", "交通", "购物", "居住", "其他"));
        seedCategory(bookId, userId, "职业收入", "INCOME", List.of("工资收入", "奖金", "兼职", "其他"));
        seedCategory(bookId, userId, "资金往来", "EXPENSE", List.of("转账", "借出", "还债"));
        seedCategory(bookId, userId, "资金往来", "INCOME", List.of("转账", "借入", "收债"));
    }

    private void seedCategory(long bookId,
                              long userId,
                              String parentName,
                              String kind,
                              List<String> children) {
        String parentPublicId = UUID.randomUUID().toString();
        String parentColor = primaryCategoryColor(kind, parentName,
                jdbc.queryForObject("SELECT COUNT(*) FROM ledger_category WHERE book_id=? AND parent_id IS NULL",
                        Integer.class, bookId));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO ledger_category(public_id,user_id,book_id,name,kind,color,created_by) VALUES(?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, parentPublicId);
            statement.setLong(2, userId);
            statement.setLong(3, bookId);
            statement.setString(4, parentName);
            statement.setString(5, kind);
            statement.setString(6, parentColor);
            statement.setLong(7, userId);
            return statement;
        }, keyHolder);
        long parentId = keyHolder.getKey().longValue();
        for (String child : children) {
            jdbc.update(
                    "INSERT INTO ledger_category(public_id,user_id,book_id,name,kind,parent_id,color,created_by) VALUES(?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), userId, bookId, child, kind, parentId,
                    childCategoryColor(parentColor, parentPublicId, children.indexOf(child)), userId);
        }
    }

    private String categoryInputColor(CategoryCommand input,
                                      long bookId,
                                      String kind,
                                      Long parentId,
                                      DbRow parent) {
        String requested = text(input == null ? null : input.color());
        if (!requested.isBlank() && !"#0f5132".equalsIgnoreCase(requested)) return requested;
        String name = required(input == null ? null : input.name(), "name");
        if (parentId == null) {
            int index = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_category WHERE book_id=? AND parent_id IS NULL",
                    Integer.class, bookId);
            return primaryCategoryColor(kind, name, index);
        }
        int index = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_category WHERE book_id=? AND parent_id=? AND deleted=FALSE",
                Integer.class, bookId, parentId);
        return childCategoryColor(
                text(parent.get("color")),
                text(parent.get("public_id")).isBlank() ? text(parent.get("name")) : text(parent.get("public_id")),
                index);
    }

    static String primaryCategoryColor(String kind, String name, int index) {
        int hue = Math.floorMod((int) Math.round(CATEGORY_HUE_BASE + Math.max(0, index) * CATEGORY_HUE_STEP), 360);
        int lightness = 46 + Math.floorMod(index, 3) * 5;
        return "hsl(" + hue + " 64% " + lightness + "%)";
    }

    static String childCategoryColor(String parentColor, String familyKey, int index) {
        int hue = Math.floorMod(String.valueOf(familyKey).hashCode(), 360);
        Matcher matcher = HSL_COLOR.matcher(parentColor == null ? "" : parentColor.trim());
        if (matcher.matches()) hue = Math.floorMod(Integer.parseInt(matcher.group(1)), 360);
        int saturation = 58 + Math.floorMod(index, 2) * 8;
        int lightness = 38 + Math.floorMod(index, 6) * 8;
        return "hsl(" + hue + " " + saturation + "% " + lightness + "%)";
    }

    private void copyResources(LedgerBookAccess.Context source, long targetBookId, long userId) {
        for (DbRow account : DbRow.query(jdbc,
                "SELECT name,icon,account_type,currency,opening_balance,hidden FROM ledger_account " +
                        "WHERE book_id=? AND deleted=FALSE ORDER BY id",
                source.bookId())) {
            jdbc.update(
                    "INSERT INTO ledger_account(public_id,user_id,book_id,name,icon,account_type,currency,opening_balance,hidden,created_by) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), userId, targetBookId, account.get("name"),
                    account.get("icon"), account.get("account_type"), account.get("currency"), account.get("opening_balance"),
                    account.get("hidden"), userId);
        }
        Map<Long, Long> categoryIds = new LinkedHashMap<>();
        for (DbRow category : DbRow.query(jdbc,
                "SELECT id,name,icon,kind,color,hidden FROM ledger_category WHERE book_id=? AND deleted=FALSE AND parent_id IS NULL ORDER BY id",
                source.bookId())) {
            long newId = insertCopiedCategory(targetBookId, userId, category, null);
            categoryIds.put(number(category.get("id")), newId);
        }
        for (DbRow category : DbRow.query(jdbc,
                "SELECT id,parent_id,name,icon,kind,color,hidden FROM ledger_category WHERE book_id=? AND deleted=FALSE AND parent_id IS NOT NULL ORDER BY id",
                source.bookId())) {
            Long parentId = categoryIds.get(number(category.get("parent_id")));
            insertCopiedCategory(targetBookId, userId, category, parentId);
        }
        for (DbRow merchant : DbRow.query(jdbc,
                "SELECT name,icon,note,hidden FROM ledger_merchant WHERE book_id=? AND deleted=FALSE ORDER BY id",
                source.bookId())) {
            jdbc.update(
                    "INSERT INTO ledger_merchant(public_id,book_id,name,icon,note,hidden,created_by) VALUES(?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), targetBookId, merchant.get("name"), merchant.get("icon"), merchant.get("note"),
                    merchant.get("hidden"), userId);
        }
        for (DbRow project : DbRow.query(jdbc,
                "SELECT name,icon,color,note,hidden FROM ledger_project WHERE book_id=? AND deleted=FALSE ORDER BY id",
                source.bookId())) {
            jdbc.update(
                    "INSERT INTO ledger_project(public_id,book_id,name,icon,color,note,hidden,created_by) VALUES(?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), targetBookId, project.get("name"), project.get("icon"), project.get("color"),
                    project.get("note"), project.get("hidden"), userId);
        }
    }

    private long insertCopiedCategory(long targetBookId,
                                      long userId,
                                      DbRow category,
                                      Long parentId) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO ledger_category(public_id,user_id,book_id,name,icon,kind,parent_id,color,hidden,created_by) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, UUID.randomUUID().toString());
            statement.setLong(2, userId);
            statement.setLong(3, targetBookId);
            statement.setString(4, String.valueOf(category.get("name")));
            statement.setString(5, String.valueOf(category.get("icon")));
            statement.setString(6, String.valueOf(category.get("kind")));
            if (parentId == null) statement.setNull(7, java.sql.Types.BIGINT);
            else statement.setLong(7, parentId);
            statement.setString(8, String.valueOf(category.get("color")));
            statement.setBoolean(9, Boolean.TRUE.equals(category.get("hidden")));
            statement.setLong(10, userId);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    private void replacePermissions(long roleId, Object raw) {
        Set<String> requested = new LinkedHashSet<>();
        if (raw instanceof Collection<?> values) {
            for (Object value : values) {
                String permission = text(value).toUpperCase(Locale.ROOT);
                if (ROLE_PERMISSIONS.contains(permission)
                        && !"BOOK_DELETE".equals(permission)
                        && !"AUDIT_ALL_CLEAR".equals(permission)
                        && !"AUDIT_ALL_READ".equals(permission)) {
                    requested.add(permission);
                }
            }
        }
        requested.add("AUDIT_SELF_READ");
        jdbc.update("DELETE FROM ledger_role_permission WHERE role_id=?", roleId);
        for (String permission : requested) {
            jdbc.update("INSERT INTO ledger_role_permission(role_id,permission_code) VALUES(?,?)",
                    roleId, permission);
        }
    }

    private void addDeleted(List<RecycleItem> target,
                            LedgerBookAccess.Context context,
                            String type,
                            String table,
                            String labelColumn,
                            boolean all) {
        String scope = all ? "" : " AND created_by=?";
        List<Object> args = new ArrayList<>();
        args.add(context.bookId());
        if (!all) args.add(context.userId());
        for (DbRow row : DbRow.query(jdbc,
                "SELECT public_id," + labelColumn + " label,created_by,revision,deleted_at FROM " + table +
                        " WHERE book_id=? AND deleted=TRUE" + scope,
                args.toArray())) {
            target.add(new RecycleItem(ResourceType.valueOf(type), String.valueOf(row.get("public_id")),
                    text(row.get("label")), text(row.get("deleted_at")), number(row.get("revision")),
                    number(row.get("created_by")), null, null));
        }
    }

    private void addDeletedTransactions(List<RecycleItem> target,
                                        LedgerBookAccess.Context context,
                                        boolean all) {
        String scope = all ? "" : " AND t.created_by=?";
        List<Object> args = new ArrayList<>();
        args.add(context.bookId());
        if (!all) args.add(context.userId());
        for (DbRow row : DbRow.query(jdbc,
                "SELECT t.public_id,t.occurred_on,t.payee,t.amount," +
                        "t.created_by,t.revision,t.deleted_at FROM ledger_transaction t " +
                        "WHERE t.book_id=? AND t.deleted=TRUE AND t.kind<>'TRANSFER_IN'" + scope,
                args.toArray())) {
            target.add(new RecycleItem(ResourceType.transaction, String.valueOf(row.get("public_id")),
                    text(row.get("payee")), text(row.get("deleted_at")), number(row.get("revision")),
                    number(row.get("created_by")), text(row.get("occurred_on")), decimal(row.get("amount"))));
        }
    }

    private boolean isReferenced(ResourceTable table, long bookId, long internalId) {
        return switch (table.type()) {
            case "account" -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=? AND (account_id=? OR counterparty_account_id=?)",
                    Long.class, bookId, internalId, internalId) > 0;
            case "category" -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=? AND category_id=?",
                    Long.class, bookId, internalId) > 0;
            case "merchant" -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=? AND merchant_id=?",
                    Long.class, bookId, internalId) > 0;
            case "project" -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=? AND project_id=?",
                    Long.class, bookId, internalId) > 0;
            case "member" -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=? AND member_id=?",
                    Long.class, bookId, internalId) > 0;
            case "role" -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_book_member WHERE role_id=?",
                    Long.class, internalId) > 0;
            case "budget" -> false;
            default -> true;
        };
    }

    private Book bookView(DbRow row) {
        return new Book(text(row.get("public_id")), text(row.get("name")), text(row.get("currency")),
                number(row.get("owner_user_id")), number(row.get("revision")), bool(row.get("archived")),
                text(row.get("role_code")), text(row.get("role_name")),
                row.get("role_permissions") == null ? List.of() : List.of(text(row.get("role_permissions")).split(",")),
                number(row.get("member_count")), number(row.get("transaction_count")), text(row.get("created_at")));
    }

    private Account accountView(DbRow row) {
        return new Account(text(row.get("public_id")), text(row.get("name")), text(row.get("icon")),
                text(row.get("account_type")), text(row.get("currency")), decimal(row.get("opening_balance")),
                decimal(row.get("balance")), bool(row.get("hidden")), number(row.get("revision")),
                text(row.get("created_at")));
    }

    private Category categoryView(DbRow row) {
        return new Category(text(row.get("public_id")), text(row.get("name")), text(row.get("icon")),
                CategoryKind.valueOf(text(row.get("kind"))), nullableText(row.get("parent_public_id")),
                text(row.get("color")), bool(row.get("hidden")), number(row.get("revision")),
                text(row.get("created_at")));
    }

    private NamedResource namedView(String type, DbRow row) {
        return new NamedResource(text(row.get("public_id")), text(row.get("name")), text(row.get("icon")),
                nullableText(row.get("note")), "project".equals(type) ? nullableText(row.get("color")) : null,
                bool(row.get("hidden")), number(row.get("revision")), text(row.get("created_at")));
    }

    private Member memberView(DbRow row) {
        String nickname = nullableText(row.get("nickname"));
        return new Member(text(row.get("public_id")), number(row.get("user_id")),
                row.get("created_by") == null ? null : number(row.get("created_by")), text(row.get("username")),
                nickname, nickname == null || nickname.isBlank() ? text(row.get("username")) : nickname,
                text(row.get("role_public_id")), text(row.get("role_code")), text(row.get("role_name")),
                text(row.get("icon")), number(row.get("revision")), text(row.get("created_at")));
    }

    private Role roleView(DbRow row) {
        String permissions = text(row.get("permissions"));
        return new Role(text(row.get("public_id")), text(row.get("code")), text(row.get("name")),
                bool(row.get("system_role")), row.get("created_by") == null ? null : number(row.get("created_by")),
                permissions.isBlank() ? List.of() : List.of(permissions.split(",")), number(row.get("revision")),
                text(row.get("created_at")));
    }

    private Budget budgetView(DbRow row) {
        return new Budget(text(row.get("public_id")), nullableText(row.get("category_public_id")),
                row.get("category") == null ? "月度总预算" : text(row.get("category")),
                row.get("category_id") == null ? "TOTAL" : "CATEGORY", text(row.get("month_key")),
                decimal(row.get("budget")), decimal(row.get("spent")), number(row.get("revision")));
    }

    private ResourceTable resourceTable(String type) {
        return switch (type) {
            case "account" -> new ResourceTable("account", "ledger_account", "账户");
            case "category" -> new ResourceTable("category", "ledger_category", "分类");
            case "merchant" -> new ResourceTable("merchant", "ledger_merchant", "商家");
            case "project" -> new ResourceTable("project", "ledger_project", "项目");
            case "budget" -> new ResourceTable("budget", "ledger_budget", "预算");
            case "role" -> new ResourceTable("role", "ledger_role", "角色");
            case "member" -> new ResourceTable("member", "ledger_book_member", "成员");
            default -> throw new IllegalArgumentException("不支持的资源类型：" + type);
        };
    }

    private ResourceTable resourceTableByName(String table) {
        return switch (table) {
            case "ledger_account" -> resourceTable("account");
            case "ledger_category" -> resourceTable("category");
            case "ledger_merchant" -> resourceTable("merchant");
            case "ledger_project" -> resourceTable("project");
            case "ledger_budget" -> resourceTable("budget");
            case "ledger_role" -> resourceTable("role");
            case "ledger_book_member" -> resourceTable("member");
            default -> throw new IllegalArgumentException("不支持的数据表");
        };
    }

    private DeletedResource tombstone(String publicId, long revision, Long createdBy) {
        return new DeletedResource(publicId, revision, true, java.time.Instant.now().toString(),
                createdBy, null, null);
    }

    private String requestedPublicId(String id) {
        String value = text(id);
        if (value.isBlank()) return UUID.randomUUID().toString();
        try {
            UUID.fromString(value);
            return value;
        } catch (Exception exception) {
            throw new IllegalArgumentException("id 必须是 UUID");
        }
    }

    private String required(Object input, String key) {
        String value = text(input);
        if (value.isBlank()) throw new IllegalArgumentException(key + " 必填");
        return value;
    }

    private String optionalText(Object input, String key, String fallback) {
        if (input == null) return fallback;
        String value = text(input);
        if (value.isBlank()) throw new IllegalArgumentException(key + " 不能为空");
        return value;
    }

    private String currency(Object value) {
        String result = textOr(value, "CNY").toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency 必须是三位货币代码");
        return result;
    }

    private String accountType(Object value) {
        String type = textOr(value, "cash").toLowerCase(Locale.ROOT);
        if (!Set.of("cash", "bank", "card", "wallet", "other").contains(type)) {
            throw new IllegalArgumentException("账户类型不正确");
        }
        return type;
    }

    private String icon(Object value, String fallback) {
        String result = textOr(value, fallback).toLowerCase(Locale.ROOT);
        if (!RESOURCE_ICONS.contains(result)) throw new IllegalArgumentException("图标类型不正确");
        return result;
    }

    private String categoryKind(Object value) {
        String kind = text(value).toUpperCase(Locale.ROOT);
        if ("收入".equals(value)) kind = "INCOME";
        if ("支出".equals(value)) kind = "EXPENSE";
        if (!Set.of("INCOME", "EXPENSE").contains(kind)) throw new IllegalArgumentException("分类类型不正确");
        return kind;
    }

    private BigDecimal amount(Object value) {
        try {
            return new BigDecimal(String.valueOf(value == null ? 0 : value))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception exception) {
            throw new IllegalArgumentException("金额格式不正确");
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private String textOr(Object value, String fallback) {
        String result = text(value);
        return result.isBlank() ? fallback : result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0;
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private void checkRevision(String ifMatch, long revision) {
        if (ifMatch == null || ifMatch.isBlank()) return;
        try {
            long expected = Long.parseLong(ifMatch.replace("W/", "").replace("\"", ""));
            if (expected != revision) throw new ConflictException("资源版本已变化", revision);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match 必须是 revision");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private record ResourceTable(String type, String table, String label) {
    }
}
