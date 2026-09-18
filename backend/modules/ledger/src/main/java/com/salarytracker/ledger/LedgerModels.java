package com.salarytracker.ledger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LedgerModels {
    private LedgerModels() {
    }

    @Schema(oneOf = {Book.class, Account.class, Category.class, NamedResource.class, Member.class,
            Role.class, Budget.class, Transaction.class, DeletedResource.class})
    public sealed interface SyncEntity permits Book, RestoreView, DeletedResource { }

    @Schema(oneOf = {Account.class, Category.class, NamedResource.class, Member.class, Role.class, Budget.class})
    public sealed interface ResourceView extends RestoreView
            permits Account, Category, NamedResource, Member, Role, Budget { }

    @Schema(oneOf = {Account.class, Category.class, NamedResource.class, Member.class, Role.class,
            Budget.class, Transaction.class})
    public sealed interface RestoreView extends SyncEntity permits ResourceView, Transaction { }

    public enum BookMode { EMPTY, SYSTEM_TEMPLATE, COPY }
    public enum ResourceType { book, account, category, merchant, project, member, role, budget, transaction }
    public enum CategoryKind { INCOME, EXPENSE }
    public enum TransactionKind { EXPENSE, INCOME, TRANSFER, BORROW_IN, LEND_OUT, COLLECT_DEBT, REPAY_DEBT }
    public enum SyncAction { UPSERT, DELETE }
    public enum SyncStatus { APPLIED, DUPLICATE, CONFLICT, FORBIDDEN, REJECTED }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BookCommand(String id, String name, String currency, BookMode mode,
                              String sourceBookId, Boolean archived) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccountCommand(String id, String name, String icon, String accountType,
                                 String currency, BigDecimal openingBalance, Boolean hidden) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CategoryCommand(String id, String name, String icon, CategoryKind kind,
                                  String parentId, String color, Boolean hidden) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NamedResourceCommand(String id, String name, String icon, String color,
                                       String note, Boolean hidden) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MemberCommand(String id, String username, String roleId, String icon) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoleCommand(String id, String code, String name, List<String> permissions) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BudgetCommand(String id, String categoryId, String scope,
                                @Schema(pattern = "^\\d{4}-(0[1-9]|1[0-2])$") String monthKey,
                                BigDecimal budget) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionCommand(
            String id, String accountId, String targetAccountId, String categoryId,
            String merchantId, String memberId, String projectId, TransactionKind kind,
            BigDecimal amount, String currency, LocalDate occurredOn, String payee,
            String member, String project, String note, String source, String clientOpId,
            Long recurringId, Long revision) { }

    public record CopyTransactionCommand(String targetBookId, LocalDate occurredOn) { }
    public record AuditClearCommand(List<Long> ids) { }
    public record AiPreviewCommand(String text) { }
    public record AiConfirmCommand(List<TransactionCommand> transactions) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Book(String id, String name, String currency, long ownerUserId, long revision,
                       boolean archived, String roleCode, String roleName, List<String> permissions,
                       long memberCount, long transactionCount, String createdAt) implements SyncEntity { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Account(String id, String name, String icon, String accountType, String currency,
                          BigDecimal openingBalance, BigDecimal balance, boolean hidden,
                          long revision, String createdAt) implements ResourceView { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Category(String id, String name, String icon, CategoryKind kind, String parentId,
                           String color, boolean hidden, long revision, String createdAt) implements ResourceView { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NamedResource(String id, String name, String icon, String note, String color,
                                boolean hidden, long revision, String createdAt) implements ResourceView { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Member(String id, long userId, Long createdBy, String username, String nickname,
                         String displayName, String roleId, String roleCode, String roleName,
                         String icon, long revision, String createdAt) implements ResourceView { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Role(String id, String code, String name, boolean systemRole, Long createdBy,
                       List<String> permissions, long revision, String createdAt) implements ResourceView { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Budget(String id, String categoryId, String category, String scope, String monthKey,
                         BigDecimal budget, BigDecimal spent, long revision) implements ResourceView { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeletedResource(String id, long revision, boolean deleted, String deletedAt,
                                  Long createdBy, Boolean anonymized, Boolean purged) implements SyncEntity { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Transaction(
            @JsonIgnore @Schema(hidden = true) long internalId,
            String id, String accountId, String accountName, String accountIcon,
            String targetAccountId, String targetAccountName, String targetAccountIcon,
            String transferGroupId, String categoryId, String categoryName, String categoryIcon,
            String categoryColor, String parentCategoryId, String parentCategoryName,
            String parentCategoryIcon, String parentCategoryColor, String merchantId,
            String merchantName, String merchantIcon, String payee, String memberId,
            String memberIcon, String memberUsername, String member, String projectId,
            String projectIcon, String projectColor, String projectName, String project,
            String storedKind, TransactionKind kind, BigDecimal amount, String currency,
            LocalDate occurredOn, String note, String source, String clientOpId, long revision,
            boolean deleted, String deletedAt, long createdBy, String createdAt, String updatedAt)
            implements RestoreView { }

    public record TransactionVersion(long revision, String operation, Transaction payload,
                                     String actor, String createdAt) { }

    public record TransactionQuery(String page, String pageSize, String from, String to, String kind,
                                   String accountId, String categoryId, String primaryCategoryId,
                                   String secondaryCategoryId, String merchantId, String memberId,
                                   String projectId, String note, String payee, String member,
                                   String project, String q, String createdBy, String sort,
                                   String direction) { }

    public record TransactionSummary(BigDecimal income, BigDecimal expense, long count) { }
    public record TransactionPage(List<Transaction> items, int page, int pageSize, long total,
                                  long totalPages, TransactionSummary summary) { }
    public record DailyTotal(LocalDate date, BigDecimal income, BigDecimal expense) { }
    public record CategoryTotal(String id, String name, String color, BigDecimal amount, long count) { }
    public record Overview(String from, String to, BigDecimal income, BigDecimal expense,
                           BigDecimal balance, List<DailyTotal> daily, List<CategoryTotal> categories,
                           List<Budget> budgets) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecycleItem(ResourceType type, String id, String name, String deletedAt,
                              long revision, long createdBy, String occurredOn, BigDecimal amount) { }
    public record RecyclePage(List<RecycleItem> items, int page, int pageSize, long total, long totalPages) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EntitySnapshot(ResourceType entityType, String id, String name, String code,
                                 String username, String displayName, String category,
                                 String categoryId, String monthKey, String accountId,
                                 String occurredOn, String payee, BigDecimal amount,
                                 Long revision, Boolean deleted) { }
    public record Actor(long id, String username, String nickname) { }
    public record AuditLog(long id, String action, ResourceType targetType, String targetId,
                           String targetName, EntitySnapshot before, EntitySnapshot after,
                           Actor actor, String createdAt) { }
    public record AuditPage(List<AuditLog> items, int page, int pageSize, long total, long totalPages) { }
    public record DeleteCount(int deleted) { }

    public record CalendarRule(String monthlyMode, Integer weekOfMonth, Integer dayOfWeek,
                               Integer dayOfMonth, Integer month) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScheduledTaskCommand(String id, String taskType, String name, Boolean enabled,
                                       String scheduleMode, String frequency, Integer intervalValue,
                                       CalendarRule calendarRule, LocalDate startOn, LocalDate endOn,
                                       Integer maxRuns, TransactionCommand payload) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScheduledTask(String id, String taskType, String name, boolean enabled,
                                String scheduleMode, String frequency, int intervalValue,
                                CalendarRule calendarRule, LocalDate startOn, LocalDate nextRunOn,
                                LocalDate endOn, Integer maxRuns, int runCount,
                                TransactionCommand payload, String lastRunAt, String lastRunStatus,
                                String lastError, long revision, boolean deleted) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScheduledTaskRun(String status, String taskId, String transactionId,
                                   LocalDate dueOn, String error) { }

    public record ImportRow(String sheet, int rowNumber, String status, List<String> errors,
                            TransactionKind kind, LocalDate occurredOn, String parentCategory,
                            String category, String account, String targetAccount, BigDecimal amount,
                            String member, String merchant, String project, String note, String source)
            { }
    public record ResourceCreates(List<String> accounts, List<String> categories,
                                  List<String> merchants, List<String> projects) { }
    public record ImportPreview(String batchId, String filename, String template,
                                int validCount, int errorCount, int duplicateCount,
                                List<ImportRow> rows, ResourceCreates toCreate) { }
    public record ImportBatchPayload(String filename, String template, List<ImportRow> rows,
                                     ResourceCreates toCreate, Long createdCount) { }
    public record ImportConfirm(String batchId, String status, long createdCount) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SyncPayload(
            String id, String name, String code, String username, String roleId, String icon,
            String color, String note, String currency, String accountType, BigDecimal openingBalance,
            Boolean hidden, Boolean archived, String kind, String parentId, String categoryId,
            String scope, String monthKey, BigDecimal budget, List<String> permissions,
            String accountId, String targetAccountId, String merchantId, String memberId,
            String projectId, BigDecimal amount, LocalDate occurredOn, String payee,
            String member, String project, String source, String clientOpId, Long revision)
            { }
    public record SyncOperationRequest(String opId, ResourceType entityType, String entityId,
                                       SyncAction operation, Long baseRevision, SyncPayload payload) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SyncOperationResult(String opId, ResourceType entityType, String entityId,
                                      SyncStatus status, SyncEntity entity, Long serverRevision,
                                      SyncEntity serverEntity, List<String> conflictFields,
                                      String message, Boolean exportRequired) { }
    public record SyncPushResponse(List<SyncOperationResult> results, long applied, long duplicates) { }
    public record SyncChange(long cursor, String opId, ResourceType entityType, String entityId,
                             SyncAction operation, SyncEntity payload, String createdAt) { }
    public record SyncPullResponse(List<SyncChange> operations, long cursor,
                                   boolean hasMore, long serverMaxCursor) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AiDraft(String id, TransactionKind kind, BigDecimal amount, LocalDate occurredOn,
                          String accountId, String accountName, String targetAccountId,
                          String targetAccountName, String categoryId, String categoryName,
                          String parentCategoryName, String categoryMatchStatus, String merchantId,
                          String merchantName, String memberId, String member, String projectId,
                          String projectName, String note, List<String> warnings) { }
    public record AiPreview(String draftId, List<AiDraft> drafts, String sourceType,
                            boolean requiresConfirmation, boolean localFallback) { }
    public record AiStoredDraft(List<AiDraft> drafts, boolean localFallback, List<Transaction> created) { }
    public record AiConfirm(String draftId, String status, List<Transaction> created) { }

    public record MetricSummary(BigDecimal income, BigDecimal expense, BigDecimal net,
                                BigDecimal incomeCount, BigDecimal expenseCount) { }
    public record PreviousMonth(BigDecimal income, BigDecimal expense, BigDecimal net) { }
    public record Ranking(String name, BigDecimal amount, BigDecimal count, BigDecimal share) { }
    public record RankedTransaction(BigDecimal amount, String date, String category,
                                    String account, String merchant) { }
    public record BudgetMetric(BigDecimal total, BigDecimal spent) { }
    public record MonthlyAnalysisCommand(String period, MetricSummary summary,
                                         PreviousMonth previousMonth, List<Ranking> expenseCategories,
                                         List<Ranking> incomeCategories, List<Ranking> merchants,
                                         List<RankedTransaction> topExpenses,
                                         List<RankedTransaction> topIncomes, BudgetMetric budget) { }
    public record MonthlyAnalysis(String headline, String summary,
                                  List<String> suggestions, List<String> risks) { }
    public record MaterializeResult(String month, int accounts) { }
}
