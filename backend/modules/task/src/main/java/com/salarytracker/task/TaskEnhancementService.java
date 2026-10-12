package com.salarytracker.task;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.salarytracker.task.TaskEnhancementModels.*;

@Service
public class TaskEnhancementService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 10_000;
    private static final Pattern TAG = Pattern.compile("(?<!\\S)#([^#@!\\s]+)");
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;
    private final TaskService tasks;
    private final ObjectMapper mapper;

    public TaskEnhancementService(JdbcTemplate jdbc, CurrentUserResolver currentUser, TaskService tasks,
                                  ObjectMapper mapper) {
        this.jdbc = jdbc; this.currentUser = currentUser; this.tasks = tasks; this.mapper = mapper;
    }

    public List<SmartFilter> filters() {
        return jdbc.query("SELECT * FROM task_filter WHERE user_id=? ORDER BY pinned DESC,sort_order,id",
                (r, n) -> filter(r), currentUser.id());
    }

    @Transactional
    public SmartFilter createFilter(SmartFilterCommand command) {
        long userId = currentUser.id();
        ValidFilter valid = validate(command);
        validateFilterReferences(userId, valid);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO task_filter(public_id,user_id,name,list_ids,tag_ids,priorities,task_status,due_range," +
                        "keyword,include_subtasks,match_all_tags,sort_order,pinned) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, userId, valid.name, join(valid.listIds), join(valid.tagIds), join(valid.priorities), valid.status,
                valid.dueRange, valid.keyword, valid.includeSubtasks, valid.matchAllTags, valid.sortOrder, valid.pinned);
        return filterForUser(id);
    }

    @Transactional
    public SmartFilter updateFilter(String id, SmartFilterCommand command, long revision) {
        SmartFilter before = filterForUser(id);
        ValidFilter valid = validate(command);
        validateFilterReferences(currentUser.id(), valid);
        int changed = jdbc.update("UPDATE task_filter SET name=?,list_ids=?,tag_ids=?,priorities=?,task_status=?," +
                        "due_range=?,keyword=?,include_subtasks=?,match_all_tags=?,sort_order=?,pinned=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND revision=?",
                valid.name, join(valid.listIds), join(valid.tagIds), join(valid.priorities), valid.status,
                valid.dueRange, valid.keyword, valid.includeSubtasks, valid.matchAllTags, valid.sortOrder, valid.pinned,
                before.publicId(), currentUser.id(), revision);
        if (changed != 1) throw new ConflictException("智能清单已被其他操作更新", before.revision());
        return filterForUser(id);
    }

    @Transactional
    public boolean deleteFilter(String id, long revision) {
        SmartFilter before = filterForUser(id);
        int changed = jdbc.update("DELETE FROM task_filter WHERE public_id=? AND user_id=? AND revision=?",
                before.publicId(), currentUser.id(), revision);
        if (changed != 1) throw new ConflictException("智能清单已被其他操作更新", before.revision());
        return true;
    }

    public TaskModels.TaskPage filterTasks(String id, int page, int size) {
        SmartFilter filter = filterForUser(id);
        List<TaskModels.TaskItem> matched = openTasks(2000).stream().filter(task -> matches(filter, task)).toList();
        int safeSize = Math.min(Math.max(size, 1), 200), safePage = Math.max(page, 0), from = safePage * safeSize;
        List<TaskModels.TaskItem> content = from >= matched.size() ? List.of()
                : matched.subList(from, Math.min(from + safeSize, matched.size()));
        return new TaskModels.TaskPage(content, safePage, safeSize, matched.size());
    }

    public Board board(String dimension) {
        String normalized = enumValue(dimension, List.of("LIST", "STATUS", "PRIORITY", "TAG"), "LIST");
        List<TaskModels.TaskItem> rows = openTasks(2000);
        Map<String, List<TaskModels.TaskItem>> groups = new LinkedHashMap<>();
        if ("LIST".equals(normalized)) for (TaskModels.TaskList list : tasks.listLists()) groups.put(list.publicId(), new ArrayList<>());
        if ("STATUS".equals(normalized)) { groups.put("OPEN", new ArrayList<>()); groups.put("COMPLETED", new ArrayList<>()); }
        if ("PRIORITY".equals(normalized)) for (String key : List.of("HIGH", "MEDIUM", "LOW", "NONE")) groups.put(key, new ArrayList<>());
        if ("TAG".equals(normalized)) { for (TaskModels.TaskTag tag : tasks.listTags()) groups.put(tag.publicId(), new ArrayList<>()); groups.put("NONE", new ArrayList<>()); }
        for (TaskModels.TaskItem task : rows) {
            if ("LIST".equals(normalized)) groups.computeIfAbsent(task.listId(), ignored -> new ArrayList<>()).add(task);
            else if ("STATUS".equals(normalized)) groups.get(task.status()).add(task);
            else if ("PRIORITY".equals(normalized)) groups.get(task.priority()).add(task);
            else if (task.tagIds().isEmpty()) groups.get("NONE").add(task);
            else task.tagIds().forEach(tag -> groups.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(task));
        }
        Map<String, String> labels = labels(normalized);
        return new Board(normalized, groups.entrySet().stream()
                .map(entry -> new BoardColumn(entry.getKey(), labels.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue())).toList());
    }

    public Board quadrant() {
        Map<String, List<TaskModels.TaskItem>> result = new LinkedHashMap<>();
        for (String key : List.of("IMPORTANT_URGENT", "IMPORTANT_NOT_URGENT", "NOT_IMPORTANT_URGENT", "NOT_IMPORTANT_NOT_URGENT")) result.put(key, new ArrayList<>());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (TaskModels.TaskItem task : openTasks(2000)) {
            boolean important = List.of("HIGH", "MEDIUM").contains(task.priority());
            boolean urgent = task.dueAt() != null && !Instant.parse(task.dueAt()).atZone(ZoneId.of(task.timezone())).toLocalDate().isAfter(today);
            result.get((important ? "IMPORTANT" : "NOT_IMPORTANT") + "_" + (urgent ? "URGENT" : "NOT_URGENT")).add(task);
        }
        Map<String, String> labels = Map.of("IMPORTANT_URGENT", "重要且紧急", "IMPORTANT_NOT_URGENT", "重要不紧急",
                "NOT_IMPORTANT_URGENT", "不重要但紧急", "NOT_IMPORTANT_NOT_URGENT", "不重要不紧急");
        return new Board("QUADRANT", result.entrySet().stream().map(e -> new BoardColumn(e.getKey(), labels.get(e.getKey()), e.getValue())).toList());
    }

    public SearchResult search(String raw) {
        String query = text(raw).trim();
        if (query.isEmpty() || query.length() > 200) throw new IllegalArgumentException("搜索词长度应为 1-200");
        String like = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%";
        List<String> ids = jdbc.query("SELECT public_id FROM task WHERE user_id=? AND deleted=FALSE " +
                        "AND (MATCH(title,description) AGAINST (? IN NATURAL LANGUAGE MODE) " +
                        "OR title LIKE ? OR description LIKE ?) ORDER BY updated_at DESC LIMIT 100",
                (r, n) -> r.getString(1), currentUser.id(), query, like, like);
        List<TaskModels.TaskItem> taskRows = ids.stream().map(tasks::get).toList();
        List<TaskModels.TaskList> lists = tasks.listLists().stream().filter(v -> contains(v.name(), query)).limit(30).toList();
        List<TaskModels.TaskTag> tags = tasks.listTags().stream().filter(v -> contains(v.name(), query)).limit(30).toList();
        return new SearchResult(taskRows, lists, tags, query);
    }

    public Stats stats(String from, String to) {
        LocalDate start = parseDate(from, LocalDate.now().withDayOfMonth(1));
        LocalDate end = parseDate(to, LocalDate.now());
        if (end.isBefore(start) || end.isAfter(start.plusYears(1))) throw new IllegalArgumentException("统计范围无效");
        long userId = currentUser.id();
        Object[] bounds = { userId, start, end.plusDays(1) };
        long created = count("SELECT COUNT(*) FROM task WHERE user_id=? AND created_at>=? AND created_at<?", bounds);
        long completed = count("SELECT COUNT(*) FROM task WHERE user_id=? AND completed_at>=? AND completed_at<?", bounds);
        long overdue = count("SELECT COUNT(*) FROM task WHERE user_id=? AND completed_at>=? AND completed_at<? AND due_at<completed_at", bounds);
        Double hours = jdbc.queryForObject("SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND,created_at,completed_at))/3600,0) FROM task WHERE user_id=? AND completed_at>=? AND completed_at<?", Double.class, bounds);
        Integer focus = jdbc.queryForObject("SELECT COALESCE(SUM(actual_minutes),0) FROM focus_session WHERE user_id=? AND status='COMPLETED' AND started_at>=? AND started_at<?", Integer.class, bounds);
        long total = created + count("SELECT COUNT(*) FROM task WHERE user_id=? AND created_at<? AND status='OPEN' AND deleted=FALSE", userId, start);
        return new Stats(start.toString(), end.toString(), created, completed, overdue, total == 0 ? 0 : completed * 100d / total,
                hours == null ? 0 : hours, focus == null ? 0 : focus,
                grouped("SELECT priority,COUNT(*) c FROM task WHERE user_id=? AND deleted=FALSE GROUP BY priority", userId),
                grouped("SELECT l.name,COUNT(*) c FROM task t JOIN task_list l ON l.id=t.list_id WHERE t.user_id=? AND t.deleted=FALSE GROUP BY l.id,l.name", userId),
                grouped("SELECT g.name,COUNT(*) c FROM task_tag_link x JOIN task_tag g ON g.id=x.tag_id JOIN task t ON t.id=x.task_id WHERE t.user_id=? AND t.deleted=FALSE GROUP BY g.id,g.name", userId));
    }

    public ParsedDraft parse(ParseCommand command) {
        String original = command == null ? "" : text(command.text()).trim();
        if (original.isEmpty() || original.length() > 500) throw new IllegalArgumentException("快速添加内容长度应为 1-500");
        String priority = original.contains("!!!") || original.toLowerCase().contains("p1") ? "HIGH"
                : original.contains("!!") || original.toLowerCase().contains("p2") ? "MEDIUM"
                : original.contains("!") || original.toLowerCase().contains("p3") ? "LOW" : "NONE";
        List<String> tags = new ArrayList<>(); Matcher matcher = TAG.matcher(original); while (matcher.find()) tags.add(matcher.group(1));
        String listName = null; Matcher listMatcher = Pattern.compile("(?<!\\S)@([^#@!\\s]+)").matcher(original); if (listMatcher.find()) listName = listMatcher.group(1);
        LocalDate date = original.contains("后天") ? LocalDate.now().plusDays(2) : original.contains("明天") ? LocalDate.now().plusDays(1) : original.contains("今天") ? LocalDate.now() : null;
        String rrule = original.contains("每天") ? "FREQ=DAILY" : original.contains("每周") ? "FREQ=WEEKLY" : original.contains("每月") ? "FREQ=MONTHLY" : "";
        String title = original.replaceAll("(?<!\\S)[#@][^#@!\\s]+", "").replace("!!!", "").replace("!!", "").replace("!", "")
                .replaceAll("(?i)\\bp[123]\\b", "").replace("今天", "").replace("明天", "").replace("后天", "")
                .replace("每天", "").replace("每周", "").replace("每月", "").trim();
        return new ParsedDraft(title.isEmpty() ? original : title, date == null ? null : date.atTime(23, 59).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toString(),
                priority, tags, listName, rrule, List.of());
    }

    @Transactional
    public ImportPreview preview(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择导入文件");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("导入文件不能超过 10 MB");
        String name = text(file.getOriginalFilename()); String lower = name.toLowerCase(Locale.ROOT);
        List<RawRow> raw = lower.endsWith(".csv") ? csvRows(file.getBytes()) : lower.endsWith(".ics") ? icsRows(file.getBytes())
                : lower.endsWith(".xlsx") || lower.endsWith(".xls") ? excelRows(file.getBytes())
                : throwUnsupported();
        if (raw.size() > MAX_ROWS) throw new IllegalArgumentException("单次导入最多 10000 行");
        Set<String> existing = new LinkedHashSet<>(jdbc.query("SELECT CONCAT(LOWER(l.name),'|',LOWER(t.title),'|',COALESCE(DATE_FORMAT(t.due_at,'%Y-%m-%dT%H:%i:%sZ'),'')) FROM task t JOIN task_list l ON l.id=t.list_id WHERE t.user_id=? AND t.deleted=FALSE", (r,n)->r.getString(1), currentUser.id()));
        Set<String> source = new LinkedHashSet<>(); List<ImportRow> rows = new ArrayList<>(); int valid=0, duplicate=0, errors=0;
        for (RawRow row : raw) {
            ImportRow normalized = normalize(row); String state = normalized.state(); String message = normalized.message();
            String fp = normalized.listName().toLowerCase(Locale.ROOT)+"|"+normalized.title().toLowerCase(Locale.ROOT)+"|"+text(normalized.dueAt());
            if ("VALID".equals(state) && (!source.add(fp) || existing.contains(fp))) { state="DUPLICATE"; message="同清单、标题和到期时间重复"; }
            if ("VALID".equals(state)) valid++; else if ("DUPLICATE".equals(state)) duplicate++; else errors++;
            rows.add(new ImportRow(normalized.rowNumber(), normalized.sheet(), normalized.title(), normalized.description(), normalized.listName(), normalized.dueAt(), normalized.priority(), normalized.tags(), normalized.status(), normalized.rrule(), state, message));
        }
        String id=UUID.randomUUID().toString(), json=json(rows);
        jdbc.update("INSERT INTO task_import_batch(public_id,user_id,file_name,rows_json,valid_count,duplicate_count,error_count,expires_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP+INTERVAL 24 HOUR)", id,currentUser.id(),name,json,valid,duplicate,errors);
        return new ImportPreview(id,name,valid,duplicate,errors,rows,Instant.now().plusSeconds(86400).toString());
    }

    @Transactional
    public ImportResult commit(ImportCommitCommand command) {
        if (command == null || text(command.previewId()).isBlank()) throw new IllegalArgumentException("previewId 不能为空");
        List<Map<String,Object>> batch=jdbc.queryForList("SELECT rows_json,committed_at FROM task_import_batch WHERE public_id=? AND user_id=? AND expires_at>CURRENT_TIMESTAMP FOR UPDATE", command.previewId(),currentUser.id());
        if(batch.isEmpty()) throw new NotFoundException("导入预览不存在或已过期");
        if(batch.get(0).get("committed_at")!=null) return new ImportResult(command.previewId(),0,0,true);
        List<ImportRow> rows=readRows(String.valueOf(batch.get(0).get("rows_json"))); Map<String,String> lists=new LinkedHashMap<>(); tasks.listLists().forEach(v->lists.put(v.name().toLowerCase(Locale.ROOT),v.publicId()));
        Map<String,String> tags=new LinkedHashMap<>(); tasks.listTags().forEach(v->tags.put(v.name().toLowerCase(Locale.ROOT),v.publicId()));
        int created=0, skipped=0;
        for(ImportRow row:rows){ if("ERROR".equals(row.state()) || ("DUPLICATE".equals(row.state()) && !Boolean.FALSE.equals(command.skipDuplicates()))){skipped++;continue;}
            String list=lists.get(row.listName().toLowerCase(Locale.ROOT)); if(list==null){var made=tasks.createList(new TaskModels.TaskListCommand(row.listName(),null,null,null,false),"task-import-list-"+command.previewId()+"-"+row.listName());list=made.publicId();lists.put(row.listName().toLowerCase(Locale.ROOT),list);}
            List<String>tagIds=new ArrayList<>();for(String tagName:row.tags()){String tag=tags.get(tagName.toLowerCase(Locale.ROOT));if(tag==null){var made=tasks.createTag(new TaskModels.TaskTagCommand(null,tagName,null,tags.size()));tag=made.publicId();tags.put(tagName.toLowerCase(Locale.ROOT),tag);}tagIds.add(tag);}
            var made=tasks.create(new TaskModels.TaskCommand(list,row.title(),row.description(),row.priority(),null,row.dueAt(),true,"Asia/Shanghai",null,null,tagIds,List.of(),row.rrule(),row.rrule().isBlank()?null:"DUE_DATE"),"task-import-"+command.previewId()+"-"+row.sheet()+"-"+row.rowNumber());if("COMPLETED".equals(row.status()))tasks.complete(made.publicId(),String.valueOf(made.revision()));created++; }
        jdbc.update("UPDATE task_import_batch SET committed_at=CURRENT_TIMESTAMP WHERE public_id=? AND user_id=? AND committed_at IS NULL",command.previewId(),currentUser.id());
        return new ImportResult(command.previewId(),created,skipped,false);
    }

    public byte[] export(ExportCommand command) {
        String format=command==null?"csv":enumValue(command.format(),List.of("CSV","XLSX","ICS"),"CSV"); boolean completed=command!=null&&Boolean.TRUE.equals(command.includeCompleted());
        List<TaskModels.TaskItem> rows=openTasks(10000).stream().filter(v->completed||!"COMPLETED".equals(v.status())).toList();
        if("ICS".equals(format)){StringBuilder out=new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//MyWorkBoard//Tasks//CN\r\n");for(var row:rows){out.append("BEGIN:VEVENT\r\nUID:").append(row.publicId()).append("\r\nSUMMARY:").append(ics(row.title())).append("\r\n");if(row.dueAt()!=null)out.append("DTEND:").append(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC")).format(Instant.parse(row.dueAt()))).append("\r\n");out.append("END:VEVENT\r\n");}return out.append("END:VCALENDAR\r\n").toString().getBytes(StandardCharsets.UTF_8);}
        List<List<String>> data=rows.stream().map(v->List.of(safe(v.title()),safe(v.description()),safe(listName(v.listId())),safe(v.dueAt()),v.priority(),v.status(),safe(String.join(";",v.tagIds())),safe(v.rrule()))).toList();
        List<String> head=List.of("标题","描述","清单","到期时间","优先级","状态","标签","重复规则"); if("XLSX".equals(format)){ByteArrayOutputStream out=new ByteArrayOutputStream();EasyExcel.write(out).head(head.stream().map(List::of).toList()).sheet("任务").doWrite(data);return out.toByteArray();}
        StringBuilder out=new StringBuilder("\ufeff").append(String.join(",",head)).append('\n'); data.forEach(row->out.append(row.stream().map(this::csvCell).reduce((a,b)->a+","+b).orElse("")).append('\n'));return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<TaskModels.TaskItem> openTasks(int limit){return jdbc.query("SELECT public_id FROM task WHERE user_id=? AND deleted=FALSE ORDER BY updated_at DESC LIMIT ?",(r,n)->r.getString(1),currentUser.id(),limit).stream().map(tasks::get).toList();}
    private SmartFilter filterForUser(String id){List<SmartFilter> rows=jdbc.query("SELECT * FROM task_filter WHERE public_id=? AND user_id=?",(r,n)->filter(r),id,currentUser.id());if(rows.isEmpty())throw new NotFoundException("智能清单不存在");return rows.get(0);}
    private SmartFilter filter(java.sql.ResultSet r)throws java.sql.SQLException{return new SmartFilter(r.getString("public_id"),r.getString("name"),split(r.getString("list_ids")),split(r.getString("tag_ids")),split(r.getString("priorities")),r.getString("task_status"),r.getString("due_range"),r.getString("keyword"),r.getBoolean("include_subtasks"),r.getBoolean("match_all_tags"),r.getInt("sort_order"),r.getBoolean("pinned"),r.getLong("revision"));}
    private boolean matches(SmartFilter f,TaskModels.TaskItem t){if(!f.listIds().isEmpty()&&!f.listIds().contains(t.listId()))return false;if(!f.priorities().isEmpty()&&!f.priorities().contains(t.priority()))return false;if(!"ALL".equals(f.status())&&!f.status().equals(t.status()))return false;if(!f.includeSubtasks()&&t.parentId()!=null)return false;if(!f.keyword().isBlank()&&!contains(t.title()+" "+t.description(),f.keyword()))return false;if(!f.tagIds().isEmpty()&&(f.matchAllTags()?!t.tagIds().containsAll(f.tagIds()):t.tagIds().stream().noneMatch(f.tagIds()::contains)))return false;LocalDate due=t.dueAt()==null?null:Instant.parse(t.dueAt()).atZone(ZoneId.of(t.timezone())).toLocalDate(),today=LocalDate.now(ZoneId.of("Asia/Shanghai"));return switch(f.dueRange()){case"TODAY"->today.equals(due);case"TOMORROW"->today.plusDays(1).equals(due);case"NEXT7"->due!=null&&!due.isBefore(today)&&due.isBefore(today.plusDays(8));case"OVERDUE"->due!=null&&due.isBefore(today)&&"OPEN".equals(t.status());case"NONE"->due==null;default->true;};}
    private ValidFilter validate(SmartFilterCommand c){if(c==null)throw new IllegalArgumentException("智能清单内容不能为空");return new ValidFilter(required(c.name(),120),clean(c.listIds()),clean(c.tagIds()),cleanEnums(c.priorities(),List.of("NONE","LOW","MEDIUM","HIGH")),enumValue(c.status(),List.of("OPEN","COMPLETED","ALL"),"OPEN"),enumValue(c.dueRange(),List.of("ANY","TODAY","TOMORROW","NEXT7","OVERDUE","NONE"),"ANY"),text(c.keyword()).trim(),!Boolean.FALSE.equals(c.includeSubtasks()),Boolean.TRUE.equals(c.matchAllTags()),c.sortOrder()==null?0:c.sortOrder(),Boolean.TRUE.equals(c.pinned()));}
    private void validateFilterReferences(long user,ValidFilter v){for(String id:v.listIds)if(count("SELECT COUNT(*) FROM task_list WHERE user_id=? AND public_id=? AND deleted=FALSE",user,id)==0)throw new IllegalArgumentException("清单不存在");for(String id:v.tagIds)if(count("SELECT COUNT(*) FROM task_tag WHERE user_id=? AND public_id=? AND deleted=FALSE",user,id)==0)throw new IllegalArgumentException("标签不存在");}
    private Map<String,String> labels(String d){Map<String,String> m=new LinkedHashMap<>();if("LIST".equals(d))tasks.listLists().forEach(v->m.put(v.publicId(),v.name()));else if("TAG".equals(d)){tasks.listTags().forEach(v->m.put(v.publicId(),v.name()));m.put("NONE","无标签");}else{m.put("OPEN","待完成");m.put("COMPLETED","已完成");m.put("HIGH","高");m.put("MEDIUM","中");m.put("LOW","低");m.put("NONE","无优先级");}return m;}
    private List<RawRow> excelRows(byte[] bytes){List<RawRow> out=new ArrayList<>();try(ExcelReader reader=EasyExcel.read(new ByteArrayInputStream(bytes)).build()){List<ReadSheet>sheets=reader.excelExecutor().sheetList();if(sheets.size()>20)throw new IllegalArgumentException("Sheet 数不能超过 20");for(ReadSheet sheet:sheets){List<Map<Integer,String>> data=EasyExcel.read(new ByteArrayInputStream(bytes)).sheet(sheet.getSheetNo()).headRowNumber(0).doReadSync();if(data.isEmpty())continue;Map<Integer,String>head=data.get(0);for(int i=1;i<data.size();i++){Map<String,String>v=new LinkedHashMap<>();for(var cell:data.get(i).entrySet())v.put(text(head.get(cell.getKey())).trim(),text(cell.getValue()).trim());if(v.values().stream().anyMatch(x->!x.isBlank()))out.add(new RawRow(sheet.getSheetName(),i+1,v));}}}return out;}
    private List<RawRow> csvRows(byte[] bytes){String content=decode(bytes);String[]lines=content.replace("\r","").split("\n");if(lines.length<2)return List.of();char sep=lines[0].chars().filter(c->c==';').count()>lines[0].chars().filter(c->c==',').count()?';':',';List<String>head=parseCsv(lines[0],sep);List<RawRow>out=new ArrayList<>();for(int i=1;i<lines.length;i++){List<String>cells=parseCsv(lines[i],sep);Map<String,String>v=new LinkedHashMap<>();for(int j=0;j<head.size();j++)v.put(head.get(j),j<cells.size()?cells.get(j):"");if(v.values().stream().anyMatch(x->!x.isBlank()))out.add(new RawRow("CSV",i+1,v));}return out;}
    private List<RawRow> icsRows(byte[] bytes){String content=decode(bytes).replace("\r\n ","");List<RawRow>out=new ArrayList<>();int row=0;for(String block:content.split("BEGIN:VEVENT")){if(!block.contains("END:VEVENT"))continue;Map<String,String>v=new LinkedHashMap<>();for(String line:block.split("\\R")){int p=line.indexOf(':');if(p>0)v.put(line.substring(0,p).split(";",2)[0],line.substring(p+1).trim());}v.put("标题",v.getOrDefault("SUMMARY",""));v.put("描述",v.getOrDefault("DESCRIPTION",""));v.put("到期时间",v.getOrDefault("DTEND",v.getOrDefault("DTSTART","")));out.add(new RawRow("ICS",++row,v));}return out;}
    private ImportRow normalize(RawRow r){String title=value(r.values,"标题","任务","任务名称","title","SUMMARY"),desc=value(r.values,"描述","备注","description"),list=value(r.values,"清单","列表","项目","list");if(list.isBlank())list="收件箱";String rawDue=value(r.values,"到期时间","到期日期","截止日期","日期","due","DTEND"),due=parseDue(rawDue);String priority=priority(value(r.values,"优先级","priority")),status=value(r.values,"状态","完成状态","status").contains("完成")?"COMPLETED":"OPEN";List<String>tags=splitMulti(value(r.values,"标签","tags"));String rrule=value(r.values,"重复规则","重复","rrule");String state=title.isBlank()?"ERROR":"VALID",message=title.isBlank()?"标题不能为空":(!rawDue.isBlank()&&due==null?"无法识别到期日期":"");if(!message.isBlank())state="ERROR";return new ImportRow(r.row,r.sheet,title,desc,list,due,priority,tags,status,rrule,state,message);}
    private String parseDue(String raw){String v=text(raw).trim();if(v.isBlank())return null;try{if(v.matches("\\d{8}T\\d{6}Z"))return LocalDateTime.parse(v,DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")).atZone(ZoneId.of("UTC")).toInstant().toString();if(v.matches("\\d{8}"))return LocalDate.parse(v,DateTimeFormatter.BASIC_ISO_DATE).atTime(23,59).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toString();String normalized=v.replace("年","-").replace("月","-").replace("日","").replace('/','-').trim();if(normalized.matches("\\d{4}-\\d{1,2}-\\d{1,2}"))return LocalDate.parse(normalized,DateTimeFormatter.ofPattern("yyyy-M-d")).atTime(23,59).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toString();return Instant.parse(v).toString();}catch(Exception e){return null;}}
    private String decode(byte[]b){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString().replace("\ufeff","");}catch(CharacterCodingException e){return new String(b,java.nio.charset.Charset.forName("GBK"));}}
    private List<String> parseCsv(String line,char sep){List<String>out=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quote=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(quote&&i+1<line.length()&&line.charAt(i+1)=='"'){cell.append('"');i++;}else quote=!quote;}else if(c==sep&&!quote){out.add(cell.toString().trim());cell.setLength(0);}else cell.append(c);}out.add(cell.toString().trim());return out;}
    private List<ImportRow> readRows(String json){try{return mapper.readValue(json,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("导入预览损坏",e);}}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Long> grouped(String sql,Object...args){Map<String,Long>out=new LinkedHashMap<>();jdbc.query(sql,r->{out.put(r.getString(1),r.getLong(2));},args);return out;}
    private long count(String sql,Object...args){Long v=jdbc.queryForObject(sql,Long.class,args);return v==null?0:v;}
    private String listName(String id){return tasks.listLists().stream().filter(v->v.publicId().equals(id)).map(TaskModels.TaskList::name).findFirst().orElse("");}
    private String safe(String v){String s=text(v);return s.matches("^[=+\\-@].*")?"'"+s:s;}
    private String csvCell(String v){return "\""+safe(v).replace("\"","\"\"")+"\"";}
    private String ics(String v){return text(v).replace("\\","\\\\").replace(",","\\,").replace(";","\\;").replace("\n","\\n");}
    private static <T> T throwUnsupported(){throw new IllegalArgumentException("仅支持 CSV、XLS、XLSX、ICS 文件");}
    private String value(Map<String,String>m,String...keys){for(String k:keys)for(var e:m.entrySet())if(e.getKey().trim().equalsIgnoreCase(k))return text(e.getValue()).trim();return "";}
    private String priority(String v){String x=text(v).toUpperCase(Locale.ROOT);if(x.contains("高")||x.equals("3")||x.equals("P1"))return"HIGH";if(x.contains("中")||x.equals("2")||x.equals("P2"))return"MEDIUM";if(x.contains("低")||x.equals("1")||x.equals("P3"))return"LOW";return"NONE";}
    private String enumValue(String v,List<String>a,String f){String x=text(v).trim().toUpperCase(Locale.ROOT);return x.isBlank()?f:a.contains(x)?x:throwEnum();}
    private String throwEnum(){throw new IllegalArgumentException("枚举值无效");}
    private String required(String v,int max){String x=text(v).trim();if(x.isEmpty()||x.length()>max)throw new IllegalArgumentException("名称长度无效");return x;}
    private List<String> clean(List<String>v){return v==null?List.of():v.stream().map(String::trim).filter(x->!x.isBlank()).distinct().toList();}
    private List<String> cleanEnums(List<String>v,List<String>a){List<String>x=clean(v).stream().map(s->s.toUpperCase(Locale.ROOT)).toList();if(!a.containsAll(x))throw new IllegalArgumentException("优先级无效");return x;}
    private List<String> split(String v){return text(v).isBlank()?List.of():Arrays.stream(v.split(",")).filter(x->!x.isBlank()).toList();}
    private List<String> splitMulti(String v){return text(v).isBlank()?List.of():Arrays.stream(v.split("[,;|，；]")).map(String::trim).filter(x->!x.isBlank()).distinct().toList();}
    private String join(List<String>v){return String.join(",",v);}
    private boolean contains(String v,String q){return text(v).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));}
    private String text(Object v){return v==null?"":String.valueOf(v);}
    private LocalDate parseDate(String v,LocalDate fallback){try{return text(v).isBlank()?fallback:LocalDate.parse(v);}catch(DateTimeParseException e){throw new IllegalArgumentException("日期格式无效");}}
    private record ValidFilter(String name,List<String>listIds,List<String>tagIds,List<String>priorities,String status,String dueRange,String keyword,boolean includeSubtasks,boolean matchAllTags,int sortOrder,boolean pinned){}
    private record RawRow(String sheet,int row,Map<String,String>values){}
}
