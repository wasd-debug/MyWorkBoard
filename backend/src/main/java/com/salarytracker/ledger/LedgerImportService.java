package com.salarytracker.ledger;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LedgerImportService {
    private static final List<String> EXPORT_HEADERS = List.of(
            "交易类型", "日期", "一级分类", "二级分类", "收入/支出账户",
            "金额", "成员", "商家", "项目", "备注");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerBookAccess access;
    private final LedgerTransactionService transactions;

    public LedgerImportService(JdbcTemplate jdbc,
                               ObjectMapper mapper,
                               LedgerBookAccess access,
                               LedgerTransactionService transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.access = access;
        this.transactions = transactions;
    }

    @Transactional
    public Map<String, Object> preview(String bookPublicId,
                                       MultipartFile file,
                                       String templateHint) throws Exception {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "IMPORT_EXPORT");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择导入文件");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".xls") && !lower.endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 CSV、XLS、XLSX 文件");
        }
        List<RawRow> rawRows = lower.endsWith(".csv")
                ? csvRows(file.getBytes())
                : excelRows(file.getBytes(), lower.endsWith(".xls") && !lower.endsWith(".xlsx"));
        if (rawRows.isEmpty()) throw new IllegalArgumentException("文件中没有可导入的数据");

        String template = recognizeTemplate(rawRows, templateHint);
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> createAccounts = new LinkedHashSet<>();
        Set<String> createCategories = new LinkedHashSet<>();
        Set<String> createMerchants = new LinkedHashSet<>();
        Set<String> createProjects = new LinkedHashSet<>();
        int valid = 0;
        int errors = 0;
        int duplicates = 0;
        for (RawRow raw : rawRows) {
            Map<String, Object> normalized = normalize(raw, template);
            List<String> rowErrors = validate(context, normalized);
            String fingerprint = fingerprint(normalized);
            boolean duplicate = !fingerprint.isBlank()
                    && (!seen.add(fingerprint) || exists(context, normalized));
            String status;
            if (!rowErrors.isEmpty()) {
                status = "ERROR";
                errors++;
            } else if (duplicate) {
                status = "DUPLICATE";
                duplicates++;
            } else {
                status = "VALID";
                valid++;
                collectCreates(context, normalized, createAccounts, createCategories, createMerchants, createProjects);
            }
            normalized.put("sheet", raw.sheet());
            normalized.put("rowNumber", raw.rowNumber());
            normalized.put("status", status);
            normalized.put("errors", rowErrors);
            rows.add(normalized);
        }

        String batchId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filename", filename);
        payload.put("template", template);
        payload.put("rows", rows);
        payload.put("toCreate", Map.of(
                "accounts", createAccounts,
                "categories", createCategories,
                "merchants", createMerchants,
                "projects", createProjects));
        jdbc.update(
                "INSERT INTO ledger_import_batch(id,book_id,user_id,template_type,payload_json,valid_count,error_count," +
                        "duplicate_count,expires_at) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP+INTERVAL 24 HOUR)",
                batchId, context.bookId(), context.userId(), template, json(payload), valid, errors, duplicates);
        return Map.of(
                "batchId", batchId,
                "filename", filename,
                "template", template,
                "validCount", valid,
                "errorCount", errors,
                "duplicateCount", duplicates,
                "rows", rows,
                "toCreate", payload.get("toCreate"));
    }

    @Transactional
    public Map<String, Object> confirm(String bookPublicId, String batchId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "IMPORT_EXPORT");
        List<Map<String, Object>> batches = jdbc.queryForList(
                "SELECT payload_json,status FROM ledger_import_batch WHERE id=? AND book_id=? AND user_id=? " +
                        "AND expires_at>CURRENT_TIMESTAMP",
                batchId, context.bookId(), context.userId());
        if (batches.isEmpty()) throw new IllegalArgumentException("导入批次不存在或已过期");
        Map<String, Object> payload = parseMap(batches.get(0).get("payload_json"));
        if ("COMMITTED".equals(batches.get(0).get("status"))) {
            return Map.of("batchId", batchId, "status", "COMMITTED",
                    "created", payload.getOrDefault("created", List.of()));
        }
        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> row : maps(payload.get("rows"))) {
            if (!"VALID".equals(row.get("status"))) continue;
            Map<String, Object> draft = resolveDraft(context, row);
            created.add(publicTransaction(transactions.create(
                    bookPublicId, draft, "import-" + batchId + "-" + row.get("rowNumber") + "-" + row.get("sheet"))));
        }
        payload.put("created", created);
        jdbc.update(
                "UPDATE ledger_import_batch SET status='COMMITTED',payload_json=?,committed_at=CURRENT_TIMESTAMP " +
                        "WHERE id=? AND status='PREVIEW'",
                json(payload), batchId);
        return Map.of("batchId", batchId, "status", "COMMITTED", "created", created, "createdCount", created.size());
    }

    public byte[] export(String bookPublicId, String format, String from, String to) {
        access.resolve(bookPublicId);
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("page", "1");
        filters.put("pageSize", "100");
        if (from != null) filters.put("from", from);
        if (to != null) filters.put("to", to);
        List<Map<String, Object>> all = new ArrayList<>();
        while (true) {
            Map<String, Object> page = transactions.list(bookPublicId, filters);
            all.addAll(maps(page.get("items")));
            if (all.size() >= number(page.get("total"))) break;
            filters.put("page", String.valueOf(Integer.parseInt(filters.get("page")) + 1));
        }
        List<List<String>> rows = all.stream().map(this::exportRow).toList();
        if ("xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            List<List<String>> head = EXPORT_HEADERS.stream().map(List::of).toList();
            EasyExcel.write(output).head(head).sheet("流水").doWrite(rows);
            return output.toByteArray();
        }
        StringBuilder csv = new StringBuilder("\ufeff");
        csv.append(EXPORT_HEADERS.stream().map(this::csvCell).reduce((a, b) -> a + "," + b).orElse("")).append('\n');
        for (List<String> row : rows) {
            csv.append(row.stream().map(this::csvCell).reduce((a, b) -> a + "," + b).orElse("")).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<RawRow> excelRows(byte[] bytes, boolean legacyXls) {
        if (legacyXls) return poiRows(bytes);
        List<RawRow> result = new ArrayList<>();
        try (ExcelReader reader = EasyExcel.read(new ByteArrayInputStream(bytes)).build()) {
            List<ReadSheet> sheets = reader.excelExecutor().sheetList();
            for (ReadSheet sheet : sheets) {
                List<Map<Integer, String>> data = EasyExcel.read(new ByteArrayInputStream(bytes))
                        .sheet(sheet.getSheetNo()).headRowNumber(0).doReadSync();
                if (data.isEmpty()) continue;
                Map<Integer, String> header = data.get(0);
                for (int rowIndex = 1; rowIndex < data.size(); rowIndex++) {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (Map.Entry<Integer, String> cell : data.get(rowIndex).entrySet()) {
                        String name = clean(header.get(cell.getKey()));
                        if (!name.isBlank()) values.put(name, clean(cell.getValue()));
                    }
                    if (values.values().stream().anyMatch(value -> !value.isBlank())) {
                        result.add(new RawRow(sheet.getSheetName(), rowIndex + 1, values));
                    }
                }
            }
        }
        return result;
    }

    private List<RawRow> poiRows(byte[] bytes) {
        List<RawRow> result = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            for (Sheet sheet : workbook) {
                Row header = sheet.getRow(sheet.getFirstRowNum());
                if (header == null) continue;
                Map<Integer, String> names = new LinkedHashMap<>();
                for (Cell cell : header) names.put(cell.getColumnIndex(), clean(formatter.formatCellValue(cell)));
                for (int rowIndex = header.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) continue;
                    Map<String, String> values = new LinkedHashMap<>();
                    for (Map.Entry<Integer, String> entry : names.entrySet()) {
                        Cell cell = row.getCell(entry.getKey());
                        String value = cell == null ? "" : clean(formatter.formatCellValue(cell));
                        if (!entry.getValue().isBlank()) values.put(entry.getValue(), value);
                    }
                    if (values.values().stream().anyMatch(value -> !value.isBlank())) {
                        result.add(new RawRow(sheet.getSheetName(), rowIndex + 1, values));
                    }
                }
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法读取 XLS 文件", exception);
        }
        return result;
    }

    private List<RawRow> csvRows(byte[] bytes) {
        String content = decode(bytes);
        List<List<String>> records = parseCsv(content);
        if (records.isEmpty()) return List.of();
        List<String> headers = records.get(0).stream().map(this::clean).toList();
        List<RawRow> result = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> record = records.get(index);
            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < Math.min(headers.size(), record.size()); column++) {
                if (!headers.get(column).isBlank()) values.put(headers.get(column), clean(record.get(column)));
            }
            if (values.values().stream().anyMatch(value -> !value.isBlank())) {
                result.add(new RawRow("CSV", index + 1, values));
            }
        }
        return result;
    }

    private String recognizeTemplate(List<RawRow> rows, String hint) {
        if (hint != null && !hint.isBlank() && !"AUTO".equalsIgnoreCase(hint)) {
            return hint.trim().toUpperCase(Locale.ROOT);
        }
        Set<String> headers = rows.get(0).values().keySet();
        if (headers.contains("微信支付账单明细") || headers.contains("交易单号") && headers.contains("支付方式")) return "WECHAT";
        if (headers.contains("支付宝交易号") || headers.contains("交易号") && headers.contains("交易对方")) return "ALIPAY";
        if (headers.stream().anyMatch(header -> header.toLowerCase(Locale.ROOT).contains("moneywiz"))) return "MONEYWIZ";
        if (headers.contains("账户1") || headers.contains("账户2") || headers.contains("子分类")) return "SUISHOUJI";
        return "STANDARD";
    }

    private Map<String, Object> normalize(RawRow raw, String template) {
        Map<String, String> row = raw.values();
        String direction = first(row, "收/支", "收支", "类型", "交易类型", "Type");
        String kind = normalizeKind(direction, raw.sheet(), first(row, "金额", "金额(元)", "金额（元）", "Amount"));
        String amount = normalizeAmount(first(row, "金额", "金额(元)", "金额（元）", "交易金额", "Amount"));
        String account = first(row, "收入/支出账户", "支出账户", "收入账户", "账户", "账户1", "支付方式", "Account");
        String targetAccount = first(row, "转入账户", "账户2", "To Account");
        String category = first(row, "二级分类", "子分类", "分类", "Category");
        String parent = first(row, "一级分类", "父分类", "主分类");
        if (category.contains(":") && parent.isBlank()) {
            String[] parts = category.split(":", 2);
            parent = parts[0].trim();
            category = parts[1].trim();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", kind);
        result.put("occurredOn", normalizeDate(first(row, "日期", "交易时间", "交易创建时间", "付款时间", "Date")));
        result.put("parentCategory", parent);
        result.put("category", category);
        result.put("account", account);
        result.put("targetAccount", targetAccount);
        result.put("amount", amount);
        result.put("member", first(row, "成员", "Member"));
        result.put("merchant", first(row, "商家", "交易对方", "商户", "Payee"));
        result.put("project", first(row, "项目", "Project"));
        result.put("note", first(row, "备注", "商品", "商品名称", "说明", "Description", "Memo"));
        result.put("source", "import-" + template.toLowerCase(Locale.ROOT));
        return result;
    }

    private List<String> validate(LedgerBookAccess.Context context, Map<String, Object> row) {
        List<String> errors = new ArrayList<>();
        if (text(row.get("kind")).isBlank()) errors.add("无法识别交易类型");
        try {
            LocalDate.parse(text(row.get("occurredOn")));
        } catch (Exception exception) {
            errors.add("日期格式不正确");
        }
        try {
            if (new BigDecimal(text(row.get("amount"))).signum() <= 0) errors.add("金额必须大于 0");
        } catch (Exception exception) {
            errors.add("金额格式不正确");
        }
        if (text(row.get("account")).isBlank()) errors.add("账户必填");
        if ("TRANSFER".equals(row.get("kind")) && text(row.get("targetAccount")).isBlank()) errors.add("转入账户必填");
        if (text(row.get("category")).isBlank()) errors.add("二级分类必填");
        String member = text(row.get("member"));
        if (!member.isBlank()) {
            long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_book_member m JOIN app_user u ON u.id=m.user_id " +
                            "WHERE m.book_id=? AND m.deleted=FALSE AND (u.username=? OR u.nickname=?)",
                    Long.class, context.bookId(), member, member);
            if (count == 0) errors.add("成员不在当前账本");
        }
        return errors;
    }

    private void collectCreates(LedgerBookAccess.Context context,
                                Map<String, Object> row,
                                Set<String> accounts,
                                Set<String> categories,
                                Set<String> merchants,
                                Set<String> projects) {
        addIfMissing(context, "ledger_account", text(row.get("account")), accounts);
        if ("TRANSFER".equals(row.get("kind"))) {
            addIfMissing(context, "ledger_account", text(row.get("targetAccount")), accounts);
        }
        String category = text(row.get("category"));
        String parent = text(row.get("parentCategory"));
        String categoryKind = Set.of("INCOME", "BORROW_IN", "COLLECT_DEBT").contains(row.get("kind"))
                ? "INCOME" : "EXPENSE";
        long categoryCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_category c LEFT JOIN ledger_category p ON p.id=c.parent_id " +
                        "WHERE c.book_id=? AND c.name=? AND c.kind=? AND c.deleted=FALSE AND (?='' OR p.name=?)",
                Long.class, context.bookId(), category, categoryKind, parent, parent);
        if (categoryCount == 0) categories.add((parent.isBlank() ? "其他" : parent) + " / " + category);
        addIfMissing(context, "ledger_merchant", text(row.get("merchant")), merchants);
        addIfMissing(context, "ledger_project", text(row.get("project")), projects);
    }

    private void addIfMissing(LedgerBookAccess.Context context, String table, String name, Set<String> target) {
        if (name.isBlank()) return;
        long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE book_id=? AND name=? AND deleted=FALSE",
                Long.class, context.bookId(), name);
        if (count == 0) target.add(name);
    }

    private boolean exists(LedgerBookAccess.Context context, Map<String, Object> row) {
        if (text(row.get("occurredOn")).isBlank() || text(row.get("amount")).isBlank()) return false;
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction t LEFT JOIN ledger_merchant m ON m.id=t.merchant_id " +
                        "WHERE t.book_id=? AND t.deleted=FALSE AND t.kind=? AND t.occurred_on=? AND t.amount=? " +
                        "AND COALESCE(m.name,t.payee,'')=? AND t.note=?",
                Long.class, context.bookId(),
                "TRANSFER".equals(row.get("kind")) ? "TRANSFER_OUT" : row.get("kind"),
                row.get("occurredOn"), row.get("amount"), row.get("merchant"), row.get("note")) > 0;
    }

    private Map<String, Object> resolveDraft(LedgerBookAccess.Context context, Map<String, Object> row) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", UUID.randomUUID().toString());
        draft.put("kind", row.get("kind"));
        draft.put("occurredOn", row.get("occurredOn"));
        draft.put("amount", row.get("amount"));
        draft.put("source", row.get("source"));
        draft.put("note", row.get("note"));
        draft.put("accountId", transactions.matchAccount(context, text(row.get("account"))));
        if ("TRANSFER".equals(row.get("kind"))) {
            draft.put("targetAccountId", transactions.matchAccount(context, text(row.get("targetAccount"))));
        }
        draft.put("categoryId", transactions.matchCategory(context,
                text(row.get("parentCategory")), text(row.get("category")), text(row.get("kind"))));
        String merchant = text(row.get("merchant"));
        if (!merchant.isBlank()) draft.put("merchantId", transactions.matchNamed(context, "merchant", merchant));
        String project = text(row.get("project"));
        if (!project.isBlank()) draft.put("projectId", transactions.matchNamed(context, "project", project));
        String member = text(row.get("member"));
        if (!member.isBlank()) draft.put("memberId", transactions.matchMember(context, member));
        return draft;
    }

    private List<String> exportRow(Map<String, Object> row) {
        String account = text(row.get("accountName"));
        if ("TRANSFER".equals(row.get("kind"))) account += " → " + text(row.get("targetAccountName"));
        return List.of(
                kindLabel(text(row.get("kind"))),
                text(row.get("occurredOn")),
                text(row.get("parentCategoryName")),
                text(row.get("categoryName")),
                account,
                text(row.get("amount")),
                text(row.get("member")),
                text(row.get("merchantName")),
                text(row.get("projectName")),
                text(row.get("note")));
    }

    private String normalizeKind(String raw, String sheet, String amount) {
        String value = text(raw).toUpperCase(Locale.ROOT);
        if (value.contains("收入") || value.equals("INCOME") || value.equals("IN")) return "INCOME";
        if (value.contains("转账") || value.equals("TRANSFER") || sheet.contains("转账")) return "TRANSFER";
        if (value.contains("借入")) return "BORROW_IN";
        if (value.contains("借出")) return "LEND_OUT";
        if (value.contains("收债")) return "COLLECT_DEBT";
        if (value.contains("还债")) return "REPAY_DEBT";
        if (value.contains("支出") || value.equals("EXPENSE") || value.equals("OUT")) return "EXPENSE";
        if (sheet.contains("收入")) return "INCOME";
        if (sheet.contains("支出")) return "EXPENSE";
        try {
            String rawAmount = text(amount).replace("¥", "").replace("￥", "").replace(",", "")
                    .replace("元", "").trim();
            return rawAmount.startsWith("-") ? "EXPENSE" : "INCOME";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeAmount(String value) {
        String cleaned = text(value).replace("¥", "").replace("￥", "").replace(",", "")
                .replace("元", "").replace("(", "-").replace(")", "").trim();
        if (cleaned.startsWith("+")) cleaned = cleaned.substring(1);
        try {
            return new BigDecimal(cleaned).abs().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (Exception ignored) {
            return cleaned;
        }
    }

    private String normalizeDate(String value) {
        String raw = text(value).replace('/', '-').replace('.', '-');
        if (raw.isBlank()) return "";
        if (raw.matches("\\d{4}-\\d{1,2}-\\d{1,2}.*")) {
            String date = raw.substring(0, Math.min(raw.length(), 10));
            String[] parts = date.split("-");
            return "%04d-%02d-%02d".formatted(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy年M月d日"),
                DateTimeFormatter.ofPattern("M-d-yyyy"),
                DateTimeFormatter.ofPattern("M-d-yy"))) {
            try {
                return LocalDate.parse(raw, formatter).toString();
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss")).toLocalDate().toString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String fingerprint(Map<String, Object> row) {
        if (text(row.get("occurredOn")).isBlank() || text(row.get("amount")).isBlank()) return "";
        return List.of("kind", "occurredOn", "amount", "account", "targetAccount", "merchant", "note")
                .stream().map(key -> text(row.get(key))).reduce((a, b) -> a + "|" + b).orElse("");
    }

    private String first(Map<String, String> row, String... names) {
        for (String name : names) {
            String direct = row.get(name);
            if (direct != null && !direct.isBlank()) return direct;
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isBlank()) return entry.getValue();
            }
        }
        return "";
    }

    private String kindLabel(String kind) {
        return switch (kind) {
            case "INCOME" -> "收入";
            case "TRANSFER" -> "转账";
            case "BORROW_IN" -> "借入";
            case "LEND_OUT" -> "借出";
            case "COLLECT_DEBT" -> "收债";
            case "REPAY_DEBT" -> "还债";
            default -> "支出";
        };
    }

    private String decode(byte[] bytes) {
        byte[] clean = bytes;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            clean = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(clean)).toString();
        } catch (CharacterCodingException ignored) {
            return Charset.forName("GB18030").decode(ByteBuffer.wrap(clean)).toString();
        }
    }

    private List<List<String>> parseCsv(String input) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < input.length() && input.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < input.length() && input.charAt(index + 1) == '\n') index++;
                row.add(cell.toString());
                cell.setLength(0);
                if (row.stream().anyMatch(value -> !value.isBlank())) records.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(current);
            }
        }
        row.add(cell.toString());
        if (row.stream().anyMatch(value -> !value.isBlank())) records.add(row);
        return records;
    }

    private String csvCell(String value) {
        return "\"" + text(value).replace("\"", "\"\"") + "\"";
    }

    private String clean(Object value) {
        return text(value).replace("\ufeff", "");
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存导入预览", exception);
        }
    }

    private Map<String, Object> parseMap(Object value) {
        try {
            return mapper.readValue(String.valueOf(value), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("导入预览数据损坏", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList();
    }

    private Map<String, Object> publicTransaction(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.remove("internalId");
        return result;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record RawRow(String sheet, int rowNumber, Map<String, String> values) {
    }
}
