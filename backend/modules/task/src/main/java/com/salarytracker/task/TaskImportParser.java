package com.salarytracker.task;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 任务导入文件解析：CSV（支持引号内换行与转义引号）、Excel（按表头筛选有效工作表）。
 */
final class TaskImportParser {
    private static final int MAX_SHEETS = 20;
    private static final List<String> TITLE_KEYS = List.of("标题", "任务", "任务名称", "title", "summary");
    private static final String NO_TITLE_HEADER = "未找到可导入的任务表：第一行表头需包含「标题」列（支持 标题 / 任务 / 任务名称 / Title）";

    private TaskImportParser() {
    }

    public record RawRow(String sheet, int row, Map<String, String> values) {
    }

    private record CsvRecord(int line, List<String> cells) {
    }

    /** 解析 CSV 字节流；引号内的换行属于同一个单元格，不会被拆成两行。 */
    static List<RawRow> csv(byte[] bytes) {
        String content = decode(bytes);
        List<CsvRecord> records = records(content, separator(content));
        if (records.isEmpty()) return List.of();
        List<String> head = records.get(0).cells().stream().map(String::trim).toList();
        if (head.stream().noneMatch(TaskImportParser::isTitleKey)) throw new IllegalArgumentException(NO_TITLE_HEADER);
        if (records.size() < 2) return List.of();
        List<RawRow> out = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            CsvRecord record = records.get(i);
            Map<String, String> values = new LinkedHashMap<>();
            for (int j = 0; j < head.size(); j++) {
                values.put(head.get(j), j < record.cells().size() ? record.cells().get(j) : "");
            }
            if (values.values().stream().anyMatch(v -> !v.isBlank())) {
                out.add(new RawRow("CSV", record.line(), values));
            }
        }
        return out;
    }

    /** 解析 Excel 字节流：只有第一行表头含「标题」列的工作表才会被导入，其余工作表跳过。 */
    static List<RawRow> excel(byte[] bytes) {
        List<RawRow> out = new ArrayList<>();
        boolean matched = false;
        try (ExcelReader reader = EasyExcel.read(new ByteArrayInputStream(bytes)).build()) {
            List<ReadSheet> sheets = reader.excelExecutor().sheetList();
            if (sheets.size() > MAX_SHEETS) throw new IllegalArgumentException("Sheet 数不能超过 20");
            for (ReadSheet sheet : sheets) {
                List<Map<Integer, String>> data = EasyExcel.read(new ByteArrayInputStream(bytes))
                        .sheet(sheet.getSheetNo()).headRowNumber(0).doReadSync();
                if (data.isEmpty() || !hasTitleHeader(data.get(0))) continue;
                matched = true;
                Map<Integer, String> head = data.get(0);
                for (int i = 1; i < data.size(); i++) {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (Map.Entry<Integer, String> cell : data.get(i).entrySet()) {
                        values.put(text(head.get(cell.getKey())).trim(), text(cell.getValue()).trim());
                    }
                    if (values.values().stream().anyMatch(v -> !v.isBlank())) {
                        out.add(new RawRow(sheet.getSheetName(), i + 1, values));
                    }
                }
            }
        }
        if (!matched) throw new IllegalArgumentException(NO_TITLE_HEADER);
        return out;
    }

    /** 任务状态归一：先排除「未完成 / 待完成」这类含「完成」字样但并非完成的表述。 */
    static String status(String raw) {
        String value = text(raw).trim();
        if (value.isEmpty()) return "OPEN";
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.equals("1") || lower.equals("true") || lower.equals("yes") || lower.equals("y")) return "COMPLETED";
        if (value.equals("0") || lower.equals("false") || lower.equals("no") || lower.equals("n")) return "OPEN";
        if (containsAny(lower, "未完成", "没完成", "没有完成", "待完成", "未开始", "未办", "进行中", "待办",
                "todo", "open", "incomplete", "pending", "not completed", "not done", "started")) return "OPEN";
        if (containsAny(lower, "完成", "已办", "done", "complete", "closed", "finished")) return "COMPLETED";
        return "OPEN";
    }

    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString().replace("\ufeff", "");
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    private static boolean containsAny(String value, String... keys) {
        for (String key : keys) if (value.contains(key)) return true;
        return false;
    }

    private static boolean hasTitleHeader(Map<Integer, String> head) {
        for (String cell : head.values()) if (isTitleKey(text(cell).trim())) return true;
        return false;
    }

    private static boolean isTitleKey(String cell) {
        for (String key : TITLE_KEYS) if (key.equalsIgnoreCase(cell)) return true;
        return false;
    }

    private static char separator(String content) {
        int end = content.indexOf('\n');
        String first = end < 0 ? content : content.substring(0, end);
        long semicolons = first.chars().filter(c -> c == ';').count();
        long commas = first.chars().filter(c -> c == ',').count();
        return semicolons > commas ? ';' : ',';
    }

    /**
     * 按 RFC 4180 切分记录：只有引号外的换行才是行分隔符，引号内换行原样保留在单元格里。
     * 返回的 line 是该记录在文件里的起始行号（1 起），用于预览定位。
     */
    private static List<CsvRecord> records(String content, char separator) {
        List<CsvRecord> out = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowStart = 1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else if (c == '\r') {
                    // 引号内的 CRLF 归一成 LF，避免描述里出现 \r
                } else {
                    if (c == '\n') line++;
                    cell.append(c);
                }
                continue;
            }
            if (c == '"') {
                quoted = true;
            } else if (c == separator) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else if (c == '\n') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
                out.add(new CsvRecord(rowStart, List.copyOf(cells)));
                cells.clear();
                line++;
                rowStart = line;
            } else if (c != '\r') {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString().trim());
            out.add(new CsvRecord(rowStart, List.copyOf(cells)));
        }
        return out;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
