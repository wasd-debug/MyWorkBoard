package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.salarytracker.ledger.LedgerModels.*;

@Service
public class LedgerScheduledTaskService {
    private static final Set<String> TYPES = Set.of("RECURRING_TRANSACTION", "STATEMENT_IMPORT");
    private static final Set<String> FREQUENCIES = Set.of("ONCE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerBookAccess access;
    private final LedgerTransactionService transactions;

    public LedgerScheduledTaskService(JdbcTemplate jdbc, ObjectMapper mapper,
                                      LedgerBookAccess access, LedgerTransactionService transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.access = access;
        this.transactions = transactions;
    }

    public List<ScheduledTask> list(String bookPublicId, boolean includeDeleted) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        String deleted = includeDeleted ? "" : " AND deleted=FALSE";
        return DbRow.query(jdbc,
                "SELECT id,public_id,task_type,name,enabled,schedule_mode,frequency,interval_value," +
                        "calendar_rule_json,start_on,next_run_on,end_on,max_runs,run_count,payload_json," +
                        "last_run_at,last_run_status,last_error,revision,deleted FROM ledger_scheduled_task " +
                        "WHERE book_id=?" + deleted + " ORDER BY enabled DESC,next_run_on,id",
                context.bookId()).stream().map(this::view).toList();
    }

    @Transactional
    public ScheduledTask create(String bookPublicId, ScheduledTaskCommand input) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        TaskValues values = values(input, null);
        String id = uuid(input == null ? null : input.id());
        jdbc.update(
                "INSERT INTO ledger_scheduled_task(public_id,book_id,task_type,name,enabled,schedule_mode," +
                        "frequency,interval_value,calendar_rule_json,start_on,next_run_on,end_on,max_runs,payload_json,created_by) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, context.bookId(), values.type(), values.name(), values.enabled(), values.scheduleMode(),
                values.frequency(), values.intervalValue(), json(values.calendarRule()), values.startOn(),
                values.firstRunOn(), values.endOn(), values.maxRuns(), json(values.payload()), context.userId());
        return byPublicId(context, id);
    }

    @Transactional
    public ScheduledTask update(String bookPublicId, String publicId,
                                ScheduledTaskCommand input, String ifMatch) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        ScheduledTask before = byPublicId(context, publicId);
        requireRevision(ifMatch, before.revision());
        TaskValues values = values(input, before);
        boolean scheduleChanged = input != null && (input.scheduleMode() != null || input.frequency() != null
                || input.intervalValue() != null || input.calendarRule() != null || input.startOn() != null);
        LocalDate nextRunOn = scheduleChanged ? values.firstRunOn() : before.nextRunOn();
        jdbc.update(
                "UPDATE ledger_scheduled_task SET task_type=?,name=?,enabled=?,schedule_mode=?,frequency=?," +
                        "interval_value=?,calendar_rule_json=?,start_on=?,next_run_on=?,end_on=?,max_runs=?," +
                        "payload_json=?,revision=revision+1 WHERE public_id=? AND book_id=? AND deleted=FALSE",
                values.type(), values.name(), values.enabled(), values.scheduleMode(), values.frequency(),
                values.intervalValue(), json(values.calendarRule()), values.startOn(), nextRunOn, values.endOn(),
                values.maxRuns(), json(values.payload()), publicId, context.bookId());
        return byPublicId(context, publicId);
    }

    @Transactional
    public DeletedResource delete(String bookPublicId, String publicId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        ScheduledTask before = byPublicId(context, publicId);
        jdbc.update("UPDATE ledger_scheduled_task SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP," +
                "enabled=FALSE,revision=revision+1 WHERE public_id=? AND book_id=? AND deleted=FALSE",
                publicId, context.bookId());
        return new DeletedResource(publicId, before.revision() + 1, true,
                java.time.Instant.now().toString(), null, null, null);
    }

    @Transactional
    public ScheduledTaskRun run(String bookPublicId, String publicId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "TRANSACTION_OWN_WRITE");
        ScheduledTask task = byPublicId(context, publicId);
        return execute(context, task, task.nextRunOn(), true);
    }

    @Transactional
    public int runDueTasks() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (DbRow row : DbRow.query(jdbc,
                "SELECT public_id,book_id FROM ledger_scheduled_task WHERE enabled=TRUE AND deleted=FALSE " +
                        "AND next_run_on<=? ORDER BY next_run_on,id", today)) {
            try {
                LedgerBookAccess.Context context = access.resolveForSystem(number(row.get("book_id")));
                ScheduledTask task = byPublicId(context, text(row.get("public_id"), ""));
                execute(context, task, task.nextRunOn(), false);
                count++;
            } catch (Exception ignored) {
                // A failed task records its own failure and must not stop the scheduler batch.
            }
        }
        return count;
    }

    private ScheduledTaskRun execute(LedgerBookAccess.Context context, ScheduledTask task,
                                     LocalDate due, boolean manual) {
        long taskId = internalId(context, task.id());
        try {
            jdbc.update("INSERT INTO ledger_scheduled_task_run(task_id,due_on,status) VALUES(?,?,?)",
                    taskId, due, "RUNNING");
        } catch (DuplicateKeyException exception) {
            return new ScheduledTaskRun("DUPLICATE", task.id(), null, due, null);
        }
        try {
            if (!"RECURRING_TRANSACTION".equals(task.taskType())) {
                throw new IllegalArgumentException("该定时任务类型暂未接入");
            }
            TransactionCommand source = task.payload();
            TransactionCommand payload = new TransactionCommand(source.id(), source.accountId(),
                    source.targetAccountId(), source.categoryId(), source.merchantId(), source.memberId(),
                    source.projectId(), source.kind(), source.amount(), source.currency(), due, source.payee(),
                    source.member(), source.project(), source.note(), "scheduled-task", source.clientOpId(),
                    taskId, source.revision());
            Transaction created = transactions.createForSystem(context, payload,
                    "scheduled:" + taskId + ":" + due);
            LocalDate next = LedgerScheduleCalculator.nextDate(due, task.scheduleMode(), task.frequency(),
                    task.intervalValue(), task.calendarRule());
            int runCount = task.runCount() + 1;
            boolean exhausted = "ONCE".equals(task.frequency())
                    || task.maxRuns() != null && runCount >= task.maxRuns()
                    || task.endOn() != null && next.isAfter(task.endOn());
            jdbc.update("UPDATE ledger_scheduled_task_run SET status='APPLIED',transaction_public_id=? " +
                    "WHERE task_id=? AND due_on=?", created.id(), taskId, due);
            jdbc.update("UPDATE ledger_scheduled_task SET next_run_on=?,run_count=?,enabled=?," +
                    "last_run_at=CURRENT_TIMESTAMP,last_run_status='APPLIED',last_error=NULL,revision=revision+1 WHERE id=?",
                    next, runCount, !exhausted, taskId);
            return new ScheduledTaskRun("APPLIED", task.id(), created.id(), due, null);
        } catch (Exception exception) {
            String message = truncate(exception.getMessage());
            jdbc.update("UPDATE ledger_scheduled_task_run SET status='FAILED',error_message=? WHERE task_id=? AND due_on=?",
                    message, taskId, due);
            jdbc.update("UPDATE ledger_scheduled_task SET last_run_at=CURRENT_TIMESTAMP,last_run_status='FAILED'," +
                    "last_error=? WHERE id=?", message, taskId);
            if (manual) throw exception instanceof RuntimeException runtime ? runtime : new IllegalStateException(exception);
            return new ScheduledTaskRun("FAILED", task.id(), null, due, message);
        }
    }

    private TaskValues values(ScheduledTaskCommand input, ScheduledTask before) {
        String type = text(input != null && input.taskType() != null ? input.taskType()
                : before == null ? null : before.taskType(), "RECURRING_TRANSACTION").toUpperCase();
        if (!TYPES.contains(type)) throw new IllegalArgumentException("任务类型不正确");
        String frequency = text(input != null && input.frequency() != null ? input.frequency()
                : before == null ? null : before.frequency(), "MONTHLY").toUpperCase();
        if (!FREQUENCIES.contains(frequency)) throw new IllegalArgumentException("频率不正确");
        String mode = text(input != null && input.scheduleMode() != null ? input.scheduleMode()
                : before == null ? null : before.scheduleMode(), LedgerScheduleCalculator.INTERVAL).toUpperCase();
        Integer requestedInterval = input != null && input.intervalValue() != null ? input.intervalValue()
                : before == null ? null : before.intervalValue();
        int interval = Math.max(1, requestedInterval == null ? 1 : requestedInterval);
        LocalDate start = input != null && input.startOn() != null ? input.startOn()
                : before == null || before.startOn() == null ? LocalDate.now() : before.startOn();
        CalendarRule rule = input != null && input.calendarRule() != null ? input.calendarRule()
                : before == null ? null : before.calendarRule();
        LedgerScheduleCalculator.validate(mode, frequency, interval, rule);
        LocalDate first = LedgerScheduleCalculator.firstDate(start, mode, frequency, interval, rule);
        LocalDate end = input != null && input.endOn() != null ? input.endOn()
                : before == null ? null : before.endOn();
        if (end != null && first.isAfter(end)) throw new IllegalArgumentException("首次执行日期不能晚于结束日期");
        Integer requestedMaxRuns = input != null && input.maxRuns() != null ? input.maxRuns()
                : before == null ? null : before.maxRuns();
        Integer maxRuns = requestedMaxRuns == null ? null : Math.max(1, requestedMaxRuns);
        TransactionCommand payload = input != null && input.payload() != null ? input.payload()
                : before == null ? null : before.payload();
        if ("RECURRING_TRANSACTION".equals(type)
                && (payload == null || payload.kind() == null || payload.amount() == null || payload.accountId() == null)) {
            throw new IllegalArgumentException("周期流水需填写类型、金额和账户");
        }
        String name = text(input != null && input.name() != null ? input.name()
                : before == null ? null : before.name(), "定时任务");
        boolean enabled = input != null && input.enabled() != null ? input.enabled()
                : before == null || before.enabled();
        return new TaskValues(type, name, enabled, mode, frequency, interval,
                rule, start, first, end, maxRuns, payload);
    }

    private ScheduledTask byPublicId(LedgerBookAccess.Context context, String id) {
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT id,public_id,task_type,name,enabled,schedule_mode,frequency,interval_value," +
                        "calendar_rule_json,start_on,next_run_on,end_on,max_runs,run_count,payload_json," +
                        "last_run_at,last_run_status,last_error,revision,deleted FROM ledger_scheduled_task " +
                        "WHERE public_id=? AND book_id=?", id, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("定时任务不存在");
        return view(rows.get(0));
    }

    private ScheduledTask view(DbRow row) {
        return new ScheduledTask(text(row.get("public_id"), ""), text(row.get("task_type"), ""),
                text(row.get("name"), ""), booleanValue(row.get("enabled"), false),
                text(row.get("schedule_mode"), LedgerScheduleCalculator.INTERVAL),
                text(row.get("frequency"), "MONTHLY"), (int) number(row.get("interval_value")),
                read(row.get("calendar_rule_json"), CalendarRule.class), date(row.get("start_on"), null),
                date(row.get("next_run_on"), null), date(row.get("end_on"), null),
                row.get("max_runs") == null ? null : (int) number(row.get("max_runs")),
                (int) number(row.get("run_count")), read(row.get("payload_json"), TransactionCommand.class),
                nullableText(row.get("last_run_at")), nullableText(row.get("last_run_status")),
                nullableText(row.get("last_error")), number(row.get("revision")),
                booleanValue(row.get("deleted"), false));
    }

    TransactionCommand payload(Object raw) {
        if (raw instanceof TransactionCommand command) return command;
        return read(raw, TransactionCommand.class);
    }

    private long internalId(LedgerBookAccess.Context context, String id) {
        return jdbc.queryForObject("SELECT id FROM ledger_scheduled_task WHERE public_id=? AND book_id=?",
                Long.class, id, context.bookId());
    }

    private <T> T read(Object raw, Class<T> type) {
        if (raw == null) return null;
        try {
            if (type.isInstance(raw)) return type.cast(raw);
            return mapper.readValue(String.valueOf(raw), type);
        } catch (Exception exception) {
            throw new IllegalArgumentException("定时任务数据格式无效", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存定时任务", exception);
        }
    }

    private String uuid(String value) {
        if (value == null || value.isBlank()) return UUID.randomUUID().toString();
        UUID.fromString(value);
        return value;
    }

    private LocalDate date(Object value, LocalDate fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return value instanceof LocalDate date ? date : LocalDate.parse(String.valueOf(value));
    }

    private String text(Object value, String fallback) {
        String result = value == null ? "" : String.valueOf(value).trim();
        return result.isBlank() ? fallback : result;
    }

    private String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long number(Object value) {
        if (value == null) return 0;
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) return fallback;
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0
                || Boolean.parseBoolean(String.valueOf(value));
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() > 490 ? value.substring(0, 490) : value;
    }

    private void requireRevision(String raw, long expected) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("If-Match 必填");
        long actual = Long.parseLong(raw.replace("W/", "").replace("\"", ""));
        if (actual != expected) throw new IllegalArgumentException("定时任务版本已变化");
    }

    private record TaskValues(String type, String name, boolean enabled, String scheduleMode,
                              String frequency, int intervalValue, CalendarRule calendarRule,
                              LocalDate startOn, LocalDate firstRunOn, LocalDate endOn,
                              Integer maxRuns, TransactionCommand payload) { }
}
