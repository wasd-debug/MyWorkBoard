package com.salarytracker.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LedgerScheduledTaskService {
    private static final Set<String> TYPES = Set.of("RECURRING_TRANSACTION", "STATEMENT_IMPORT");
    private static final Set<String> FREQUENCIES = Set.of("ONCE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerBookAccess access;
    private final LedgerTransactionService transactions;

    public LedgerScheduledTaskService(JdbcTemplate jdbc, ObjectMapper mapper, LedgerBookAccess access, LedgerTransactionService transactions) {
        this.jdbc = jdbc; this.mapper = mapper; this.access = access; this.transactions = transactions;
    }

    public List<Map<String,Object>> list(String bookPublicId, boolean includeDeleted) {
        LedgerBookAccess.Context c = access.resolve(bookPublicId);
        String deleted = includeDeleted ? "" : " AND deleted=FALSE";
        return jdbc.queryForList("SELECT id,public_id,book_id,task_type,name,enabled,frequency,interval_value,start_on,next_run_on,end_on,max_runs,run_count,payload_json,last_run_at,last_run_status,last_error,revision,deleted,created_at,updated_at FROM ledger_scheduled_task WHERE book_id=?" + deleted + " ORDER BY enabled DESC,next_run_on,id", c.bookId()).stream().map(this::view).toList();
    }

    @Transactional
    public Map<String,Object> create(String bookPublicId, Map<String,Object> input) {
        LedgerBookAccess.Context c = access.resolve(bookPublicId); access.require(c, "RESOURCE_MANAGE");
        TaskValues v = values(input, null);
        String id = uuid(input == null ? null : input.get("id"));
        jdbc.update("INSERT INTO ledger_scheduled_task(public_id,book_id,task_type,name,enabled,frequency,interval_value,start_on,next_run_on,end_on,max_runs,payload_json,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", id,c.bookId(),v.type,v.name,v.enabled,v.frequency,v.intervalValue,v.startOn,v.startOn,v.endOn,v.maxRuns,json(v.payload),c.userId());
        return byPublicId(c, id);
    }

    @Transactional
    public Map<String,Object> update(String bookPublicId, String publicId, Map<String,Object> input, String ifMatch) {
        LedgerBookAccess.Context c = access.resolve(bookPublicId); access.require(c, "RESOURCE_MANAGE");
        Map<String,Object> before = byPublicId(c, publicId); requireRevision(ifMatch, number(before.get("revision")));
        TaskValues v = values(input, before);
        jdbc.update("UPDATE ledger_scheduled_task SET task_type=?,name=?,enabled=?,frequency=?,interval_value=?,start_on=?,next_run_on=?,end_on=?,max_runs=?,payload_json=?,revision=revision+1 WHERE public_id=? AND book_id=? AND deleted=FALSE",v.type,v.name,v.enabled,v.frequency,v.intervalValue,v.startOn, before.get("nextRunOn"),v.endOn,v.maxRuns,json(v.payload),publicId,c.bookId());
        return byPublicId(c, publicId);
    }

    @Transactional
    public Map<String,Object> delete(String bookPublicId, String publicId) {
        LedgerBookAccess.Context c = access.resolve(bookPublicId); access.require(c, "RESOURCE_MANAGE");
        byPublicId(c, publicId);
        jdbc.update("UPDATE ledger_scheduled_task SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,enabled=FALSE,revision=revision+1 WHERE public_id=? AND book_id=? AND deleted=FALSE",publicId,c.bookId());
        return Map.of("id",publicId,"deleted",true);
    }

    @Transactional
    public Map<String,Object> run(String bookPublicId, String publicId) {
        LedgerBookAccess.Context c = access.resolve(bookPublicId); access.require(c, "TRANSACTION_OWN_WRITE");
        Map<String,Object> task = byPublicId(c, publicId);
        return execute(c, task, LocalDate.parse(String.valueOf(task.get("nextRunOn"))), true);
    }

    @Transactional
    public int runDueTasks() {
        LocalDate today = LocalDate.now(); int count = 0;
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT public_id,book_id FROM ledger_scheduled_task WHERE enabled=TRUE AND deleted=FALSE AND next_run_on<=? ORDER BY next_run_on,id", today);
        for (Map<String,Object> row : rows) {
            try { LedgerBookAccess.Context c = access.resolveForSystem(number(row.get("book_id"))); Map<String,Object> task = byPublicId(c,String.valueOf(row.get("public_id"))); execute(c,task,LocalDate.parse(String.valueOf(task.get("nextRunOn"))),false); count++; } catch (Exception ignored) { }
        }
        return count;
    }

    private Map<String,Object> execute(LedgerBookAccess.Context c, Map<String,Object> task, LocalDate due, boolean manual) {
        long taskId = number(task.get("internalId"));
        try {
            jdbc.update("INSERT INTO ledger_scheduled_task_run(task_id,due_on,status) VALUES(?,?,?)", taskId,due,"RUNNING");
        } catch (DuplicateKeyException e) { return Map.of("status","DUPLICATE","taskId",task.get("id"),"dueOn",due); }
        String txId = null;
        try {
            if (!"RECURRING_TRANSACTION".equals(task.get("taskType"))) throw new IllegalArgumentException("该定时任务类型暂未接入");
            Map<String,Object> payload = payload(task.get("payload"));
            payload.put("occurredOn", due.toString()); payload.put("source", "scheduled-task"); payload.put("recurringId", taskId);
            Map<String,Object> created = transactions.createForSystem(c, payload, "scheduled:" + taskId + ":" + due);
            txId = String.valueOf(created.get("id"));
            LocalDate next = nextDate(due, String.valueOf(task.get("frequency")), number(task.get("intervalValue")));
            int runCount = (int)number(task.get("runCount")) + 1;
            boolean exhausted = "ONCE".equals(task.get("frequency")) || (task.get("maxRuns") != null && runCount >= number(task.get("maxRuns"))) || (task.get("endOn") != null && next.isAfter(LocalDate.parse(String.valueOf(task.get("endOn")))));
            jdbc.update("UPDATE ledger_scheduled_task_run SET status='APPLIED',transaction_public_id=? WHERE task_id=? AND due_on=?",txId,taskId,due);
            jdbc.update("UPDATE ledger_scheduled_task SET next_run_on=?,run_count=?,enabled=?,last_run_at=CURRENT_TIMESTAMP,last_run_status='APPLIED',last_error=NULL,revision=revision+1 WHERE id=?",next,runCount,!exhausted,taskId);
            return Map.of("status","APPLIED","taskId",task.get("id"),"transactionId",txId,"dueOn",due);
        } catch (Exception e) {
            jdbc.update("UPDATE ledger_scheduled_task_run SET status='FAILED',error_message=? WHERE task_id=? AND due_on=?", truncate(e.getMessage()),taskId,due);
            jdbc.update("UPDATE ledger_scheduled_task SET last_run_at=CURRENT_TIMESTAMP,last_run_status='FAILED',last_error=? WHERE id=?",truncate(e.getMessage()),taskId);
            if (manual) throw e instanceof RuntimeException r ? r : new IllegalStateException(e);
            return Map.of("status","FAILED","taskId",task.get("id"),"error",truncate(e.getMessage()));
        }
    }

    private TaskValues values(Map<String,Object> input, Map<String,Object> before) {
        Map<String,Object> value = new LinkedHashMap<>(); if (before != null) value.putAll(before); if (input != null) value.putAll(input);
        String type = text(value.get("taskType"), "RECURRING_TRANSACTION").toUpperCase(); if (!TYPES.contains(type)) throw new IllegalArgumentException("任务类型不正确");
        String frequency = text(value.get("frequency"), "MONTHLY").toUpperCase(); if (!FREQUENCIES.contains(frequency)) throw new IllegalArgumentException("频率不正确");
        int interval = Math.max(1, (int)number(value.getOrDefault("intervalValue", 1))); LocalDate start = LocalDate.parse(text(value.get("startOn"), LocalDate.now().toString()));
        LocalDate end = text(value.get("endOn"), "").isBlank() ? null : LocalDate.parse(text(value.get("endOn"), ""));
        Integer max = text(value.get("maxRuns"), "").isBlank() ? null : Math.max(1,(int)number(value.get("maxRuns")));
        Map<String,Object> payload = value.get("payload") instanceof Map<?,?> m ? new LinkedHashMap<>((Map)m) : new LinkedHashMap<>();
        if ("RECURRING_TRANSACTION".equals(type)) { if (payload.get("kind") == null || payload.get("amount") == null || payload.get("accountId") == null) throw new IllegalArgumentException("周期流水需填写类型、金额和账户"); }
        return new TaskValues(type,text(value.get("name"),"定时任务"),Boolean.parseBoolean(String.valueOf(value.getOrDefault("enabled",true))),frequency,interval,start,end,max,payload);
    }

    private Map<String,Object> byPublicId(LedgerBookAccess.Context c,String id){ List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,public_id,book_id,task_type,name,enabled,frequency,interval_value,start_on,next_run_on,end_on,max_runs,run_count,payload_json,last_run_at,last_run_status,last_error,revision,deleted,created_at,updated_at FROM ledger_scheduled_task WHERE public_id=? AND book_id=?",id,c.bookId()); if(rows.isEmpty()) throw new IllegalArgumentException("定时任务不存在"); return view(rows.get(0)); }
    private Map<String,Object> view(Map<String,Object> r){ Map<String,Object> v=new LinkedHashMap<>(); v.put("internalId",r.get("id")); v.put("id",r.get("public_id")); v.put("taskType",r.get("task_type")); v.put("name",r.get("name")); v.put("enabled",r.get("enabled")); v.put("frequency",r.get("frequency")); v.put("intervalValue",r.get("interval_value")); v.put("startOn",String.valueOf(r.get("start_on"))); v.put("nextRunOn",String.valueOf(r.get("next_run_on"))); v.put("endOn",r.get("end_on")); v.put("maxRuns",r.get("max_runs")); v.put("runCount",r.get("run_count")); v.put("payload",payload(r.get("payload_json"))); v.put("lastRunAt",r.get("last_run_at")); v.put("lastRunStatus",r.get("last_run_status")); v.put("lastError",r.get("last_error")); v.put("revision",r.get("revision")); v.put("deleted",r.get("deleted")); return v; }
    private Map<String,Object> payload(Object raw){ try{return mapper.readValue(String.valueOf(raw),new TypeReference<Map<String,Object>>(){});}catch(Exception e){return new LinkedHashMap<>();} }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){return "{}";}}
    private LocalDate nextDate(LocalDate d,String f,long i){return switch(f){case "ONCE"->d;case "DAILY"->d.plusDays(i);case "WEEKLY"->d.plusWeeks(i);case "YEARLY"->d.plusYears(i);default->d.plusMonths(i);};}
    private String uuid(Object v){String s=text(v,"");if(s.isBlank())return UUID.randomUUID().toString();UUID.fromString(s);return s;}
    private String text(Object v,String f){return v==null||String.valueOf(v).trim().isBlank()?f:String.valueOf(v).trim();}
    private long number(Object v){if(v==null)return 0;return v instanceof Number n?n.longValue():Long.parseLong(String.valueOf(v));}
    private String truncate(String s){if(s==null)return "";return s.length()>490?s.substring(0,490):s;}
    private void requireRevision(String raw,long expected){if(raw==null||raw.isBlank())throw new IllegalArgumentException("If-Match 必填");long got=Long.parseLong(raw.replace("W/","").replace("\"", ""));if(got!=expected)throw new IllegalArgumentException("定时任务版本已变化");}
    private record TaskValues(String type,String name,boolean enabled,String frequency,int intervalValue,LocalDate startOn,LocalDate endOn,Integer maxRuns,Map<String,Object> payload){}
}
