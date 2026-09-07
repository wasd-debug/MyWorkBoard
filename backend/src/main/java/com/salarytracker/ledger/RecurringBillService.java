package com.salarytracker.ledger;

import com.salarytracker.identity.CurrentUserResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecurringBillService {
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;

    public RecurringBillService(JdbcTemplate jdbc, CurrentUserResolver currentUser) { this.jdbc = jdbc; this.currentUser = currentUser; }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT r.id,r.account_id,a.name account_name,r.category_id,c.name category_name,r.kind,r.amount,r.currency,r.title,r.frequency,r.next_due,r.note,r.active,r.revision,r.last_generated_at FROM recurring_bill r JOIN ledger_account a ON a.id=r.account_id LEFT JOIN ledger_category c ON c.id=r.category_id WHERE r.user_id=? AND r.deleted=FALSE ORDER BY r.next_due,r.id", currentUser.id()).stream().map(this::view).toList();
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        long user = currentUser.id(); long account = requiredLong(body, "accountId"); ensureAccount(account, user); Long category = longValue(body.get("categoryId")); if (category != null) ensureCategory(category, user);
        BigDecimal amount = amount(body.get("amount")); String title = required(body, "title"); String due = String.valueOf(body.getOrDefault("nextDue", LocalDate.now())); LocalDate.parse(due);
        jdbc.update("INSERT INTO recurring_bill(user_id,account_id,category_id,kind,amount,currency,title,frequency,next_due,note) VALUES(?,?,?,?,?,?,?,?,?,?)", user, account, category, kind(body.get("kind")), amount, String.valueOf(body.getOrDefault("currency", "CNY")), title, frequency(body.get("frequency")), due, String.valueOf(body.getOrDefault("note", "")));
        long id = jdbc.queryForObject("SELECT id FROM recurring_bill WHERE user_id=? ORDER BY id DESC LIMIT 1", Long.class, user); return get(id, user);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body) {
        long user = currentUser.id(); Map<String,Object> current=get(id,user); long account=longValue(body.getOrDefault("accountId",current.get("accountId"))); ensureAccount(account,user); Long category=longValue(body.getOrDefault("categoryId",current.get("categoryId"))); if(category!=null)ensureCategory(category,user); String due=String.valueOf(body.getOrDefault("nextDue",current.get("nextDue")));LocalDate.parse(due);
        jdbc.update("UPDATE recurring_bill SET account_id=?,category_id=?,kind=?,amount=?,title=?,frequency=?,next_due=?,note=?,active=?,revision=revision+1 WHERE id=? AND user_id=? AND deleted=FALSE",account,category,kind(body.getOrDefault("kind",current.get("kind"))),amount(body.getOrDefault("amount",current.get("amount"))),String.valueOf(body.getOrDefault("title",current.get("title"))),frequency(body.getOrDefault("frequency",current.get("frequency"))),due,String.valueOf(body.getOrDefault("note",current.get("note"))),booleanValue(body.getOrDefault("active",current.get("active"))),id,user);return get(id,user);
    }

    @Transactional
    public Map<String,Object> delete(long id){long user=currentUser.id();Map<String,Object> current=get(id,user);jdbc.update("UPDATE recurring_bill SET deleted=TRUE,active=FALSE,revision=revision+1 WHERE id=? AND user_id=?",id,user);return Map.of("id",id,"deleted",true,"revision",number(current.get("revision"))+1);}

    @Transactional
    public int runDue() {
        Integer locked = jdbc.queryForObject("SELECT GET_LOCK('salary-tracker-ledger-recurring', 2)", Integer.class);
        if (!Integer.valueOf(1).equals(locked)) return 0;
        try {
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> due = jdbc.queryForList("SELECT * FROM recurring_bill WHERE active=TRUE AND deleted=FALSE AND next_due<=? ORDER BY next_due,id", today);
            int generated = 0;
            for (Map<String, Object> bill : due) {
                long id = number(bill.get("id")); long user = number(bill.get("user_id"));
                String op = "recurring-" + id + "-" + String.valueOf(bill.get("next_due"));
                List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM ledger_transaction WHERE user_id=? AND client_op_id=?", user, op);
                if (existing.isEmpty()) {
                    jdbc.update("INSERT INTO ledger_transaction(user_id,account_id,category_id,kind,amount,currency,occurred_on,payee,note,source,recurring_id,client_op_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", user, bill.get("account_id"), bill.get("category_id"), bill.get("kind"), bill.get("amount"), bill.get("currency"), bill.get("next_due"), bill.get("title"), bill.get("note"), "recurring", id, op);
                    generated++;
                }
                String frequency = String.valueOf(bill.get("frequency"));
                String next = "WEEKLY".equalsIgnoreCase(frequency) ? "DATE_ADD(next_due, INTERVAL 7 DAY)" : "YEARLY".equalsIgnoreCase(frequency) ? "DATE_ADD(next_due, INTERVAL 1 YEAR)" : "DATE_ADD(next_due, INTERVAL 1 MONTH)";
                jdbc.update("UPDATE recurring_bill SET next_due=" + next + ",last_generated_at=CURRENT_TIMESTAMP,revision=revision+1 WHERE id=?", id);
            }
            return generated;
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK('salary-tracker-ledger-recurring')", Integer.class);
        }
    }

    private Map<String,Object> get(long id,long user){List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.id,r.account_id,a.name account_name,r.category_id,c.name category_name,r.kind,r.amount,r.currency,r.title,r.frequency,r.next_due,r.note,r.active,r.revision,r.last_generated_at FROM recurring_bill r JOIN ledger_account a ON a.id=r.account_id LEFT JOIN ledger_category c ON c.id=r.category_id WHERE r.id=? AND r.user_id=? AND r.deleted=FALSE",id,user);if(rows.isEmpty())throw new IllegalArgumentException("周期账单不存在");return view(rows.get(0));}
    private Map<String,Object> view(Map<String,Object> row){Map<String,Object> v=new LinkedHashMap<>();v.put("id",number(row.get("id")));v.put("accountId",number(row.get("account_id")));v.put("accountName",row.get("account_name"));v.put("categoryId",row.get("category_id"));v.put("categoryName",row.get("category_name"));v.put("kind",row.get("kind"));v.put("amount",row.get("amount"));v.put("currency",row.get("currency"));v.put("title",row.get("title"));v.put("frequency",row.get("frequency"));v.put("nextDue",String.valueOf(row.get("next_due")));v.put("note",row.get("note"));v.put("active",row.get("active"));v.put("revision",number(row.get("revision")));v.put("lastGeneratedAt",row.get("last_generated_at"));return v;}
    private void ensureAccount(long id,long user){if(jdbc.queryForList("SELECT id FROM ledger_account WHERE id=? AND user_id=? AND deleted=FALSE",id,user).isEmpty())throw new IllegalArgumentException("账户不存在");}
    private void ensureCategory(long id,long user){if(jdbc.queryForList("SELECT id FROM ledger_category WHERE id=? AND user_id=? AND deleted=FALSE",id,user).isEmpty())throw new IllegalArgumentException("分类不存在");}
    private String required(Map<String,Object> map,String key){if(map==null||map.get(key)==null||String.valueOf(map.get(key)).isBlank())throw new IllegalArgumentException(key+" 必填");return String.valueOf(map.get(key));}
    private long requiredLong(Map<String,Object> map,String key){Long value=longValue(map.get(key));if(value==null)throw new IllegalArgumentException(key+" 必填");return value;}
    private Long longValue(Object value){try{return value==null||String.valueOf(value).isBlank()?null:Long.parseLong(String.valueOf(value));}catch(Exception e){return null;}}
    private long number(Object value){return value instanceof Number n?n.longValue():Long.parseLong(String.valueOf(value));}
    private BigDecimal amount(Object value){try{return new BigDecimal(String.valueOf(value)).abs().setScale(2,java.math.RoundingMode.HALF_UP);}catch(Exception e){throw new IllegalArgumentException("amount 格式不正确");}}
    private String kind(Object value){return "INCOME".equalsIgnoreCase(String.valueOf(value))||"收入".equals(String.valueOf(value))?"INCOME":"EXPENSE";}
    private String frequency(Object value){String text=String.valueOf(value==null?"MONTHLY":value).toUpperCase();return List.of("WEEKLY","MONTHLY","YEARLY").contains(text)?text:"MONTHLY";}
    private boolean booleanValue(Object value){return value instanceof Boolean b?b:Boolean.parseBoolean(String.valueOf(value));}
}
