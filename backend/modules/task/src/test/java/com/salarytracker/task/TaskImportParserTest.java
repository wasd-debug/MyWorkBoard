package com.salarytracker.task;

import com.alibaba.excel.EasyExcel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskImportParserTest {

    @Test
    void keepsQuotedNewlinesInsideOneRecord() {
        String csv = "标题,描述,清单\n"
                + "\"写周报\",\"第一行\n第二行\",工作\n"
                + "买菜,,生活\n";
        List<TaskImportParser.RawRow> rows = TaskImportParser.csv(csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, rows.size());
        assertEquals("写周报", rows.get(0).values().get("标题"));
        assertEquals("第一行\n第二行", rows.get(0).values().get("描述"));
        assertEquals(2, rows.get(0).row(), "首条数据应位于表头下一行");
        assertEquals("买菜", rows.get(1).values().get("标题"));
        assertEquals(4, rows.get(1).row(), "跨行单元格之后的行号要按物理行顺延");
    }

    @Test
    void keepsEscapedQuotesAndSemicolonSeparator() {
        String csv = "标题;描述\n\"说\"\"你好\"\"\";x\n";
        List<TaskImportParser.RawRow> rows = TaskImportParser.csv(csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, rows.size());
        assertEquals("说\"你好\"", rows.get(0).values().get("标题"));
    }

    @Test
    void treatsUnfinishedStatusAsOpen() {
        assertEquals("COMPLETED", TaskImportParser.status("已完成"));
        assertEquals("COMPLETED", TaskImportParser.status("完成"));
        assertEquals("COMPLETED", TaskImportParser.status("Completed"));
        assertEquals("COMPLETED", TaskImportParser.status("1"));
        assertEquals("OPEN", TaskImportParser.status("未完成"), "「未完成」不能被判为已完成");
        assertEquals("OPEN", TaskImportParser.status("待完成"));
        assertEquals("OPEN", TaskImportParser.status("进行中"));
        assertEquals("OPEN", TaskImportParser.status("0"));
        assertEquals("OPEN", TaskImportParser.status("Incomplete"));
        assertEquals("OPEN", TaskImportParser.status(""));
    }

    @Test
    void importsOnlySheetsWhoseHeaderHasTitleColumn() {
        byte[] xlsx = workbook();

        List<TaskImportParser.RawRow> rows = TaskImportParser.excel(xlsx);

        assertEquals(2, rows.size(), "只有含「标题」表头的工作表应被导入");
        assertEquals("待办", rows.get(0).sheet());
        assertEquals("写周报", rows.get(0).values().get("标题"));
        assertEquals("OPEN", TaskImportParser.status(rows.get(0).values().get("状态")));
        assertEquals("COMPLETED", TaskImportParser.status(rows.get(1).values().get("状态")));
    }

    @Test
    void rejectsFileWithoutTitleHeader() {
        String csv = "时间,专注时长\n2026-11-01,25\n";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TaskImportParser.csv(csv.getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("标题"));

        byte[] noHeader = sheet("专注", List.of("时间", "时长"), List.of(List.of("2026-11-01", "25")));
        assertThrows(IllegalArgumentException.class, () -> TaskImportParser.excel(noHeader));
    }

    /** 两个工作表：第一个是专注流水（无标题列），第二个才是任务表。 */

    @Test
    void parsesChecklistColumn() {
        var items = TaskImportParser.checklist("[x] 上班打卡; [ ] 下班打卡；☑ 工时记录 | 未标记项");
        assertEquals(4, items.size());
        assertTrue(items.get(0).completed());
        assertEquals("上班打卡", items.get(0).title());
        assertFalse(items.get(1).completed());
        assertTrue(items.get(2).completed());
        assertEquals("工时记录", items.get(2).title());
        assertFalse(items.get(3).completed());
        assertEquals("未标记项", items.get(3).title());
        assertTrue(TaskImportParser.checklist("").isEmpty());
    }

    private byte[] workbook() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.alibaba.excel.ExcelWriter writer = EasyExcel.write(out).build();
        try {
            writer.write(List.of(List.of("时间", "时长"), List.of("2026-11-01 10:00", "25")), EasyExcel.writerSheet(0, "专注").head(head("时间", "时长")).build());
            writer.write(List.of(List.of("写周报", "OPEN"), List.of("已办完的事", "已完成")),
                    EasyExcel.writerSheet(1, "待办").head(head("标题", "状态")).build());
        } finally {
            writer.finish();
        }
        return out.toByteArray();
    }

    private byte[] sheet(String name, List<String> head, List<List<String>> data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).sheet(name).head(headOf(head)).doWrite(data);
        return out.toByteArray();
    }

    private List<List<String>> head(String... names) {
        return java.util.Arrays.stream(names).map(List::of).toList();
    }

    private List<List<String>> headOf(List<String> names) {
        return names.stream().map(List::of).toList();
    }
}
