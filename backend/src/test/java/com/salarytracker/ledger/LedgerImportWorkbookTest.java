package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LedgerImportWorkbookTest {
    @Test
    void previewsEverySheetAndRetainsLaterTransfersWithoutCategories() throws Exception {
        byte[] workbook;
        try (XSSFWorkbook excel = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addSheet(excel, "转账",
                    new String[] {"交易类型", "日期", "转出账户", "转入账户", "金额"},
                    new String[] {"转账", "2026-09-08 12:00:00", "现金", "银行卡", "20"});
            addSheet(excel, "支出",
                    new String[] {"交易类型", "日期", "一级分类", "二级分类", "支出账户", "金额"},
                    new String[] {"支出", "2026-09-08 12:00:00", "食品", "早餐", "现金", "10"});
            addSheet(excel, "收入",
                    new String[] {"交易类型", "日期", "一级分类", "二级分类", "收入账户", "金额"},
                    new String[] {"收入", "2026-09-07 12:00:00", "工资", "薪水", "银行卡", "100"});
            excel.write(output);
            workbook = output.toByteArray();
        }

        Map<String, Object> preview = service().preview("test-book",
                new MockMultipartFile("file", "test.xlsx", null, workbook), "AUTO");
        assertEquals(3, preview.get("validCount"));
        assertEquals(0, preview.get("errorCount"));
        List<Map<String, Object>> rows = rows(preview);
        assertEquals(List.of("转账", "支出", "收入"),
                rows.stream().map(row -> String.valueOf(row.get("sheet"))).toList());
        assertEquals("TRANSFER", rows.get(0).get("kind"));
        assertEquals("2026-09-08", rows.get(0).get("occurredOn"));
        assertEquals("银行卡", rows.get(0).get("targetAccount"));
    }

    @Test
    void previewsProvidedWorkbookWithoutDroppingLateRows() throws Exception {
        String fixture = System.getProperty("ledger.import.fixture", "");
        assumeTrue(!fixture.isBlank() && Files.isRegularFile(Path.of(fixture)));
        Map<String, Object> preview = service().preview("test-book",
                new MockMultipartFile("file", "fixture.xlsx", null, Files.readAllBytes(Path.of(fixture))),
                "AUTO");
        List<Map<String, Object>> rows = rows(preview);
        // The workbook contains one real record on the 退款 sheet in addition to
        // the transfer, expense, income and borrow sheets.
        assertEquals(7661, rows.size());
        assertEquals(534, rows.stream().filter(row -> "TRANSFER".equals(row.get("kind"))).count());
        assertEquals(0, preview.get("errorCount"));
        assertEquals(0, preview.get("duplicateCount"));
        assertEquals(7661, preview.get("validCount"));
        assertTrue(rows.stream().anyMatch(row -> "TRANSFER".equals(row.get("kind"))
                && "2026-09-08".equals(row.get("occurredOn"))));
        assertEquals(478, rows.stream().filter(row ->
                String.valueOf(row.get("occurredOn")).compareTo("2026-06-04") >= 0).count());
    }

    @Test
    void confirmsTransferAsOneCategoryFreeDraftWithBothAccounts() throws Exception {
        String[] savedPayload = new String[1];
        List<Map<String, Object>> created = new ArrayList<>();
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                if (sql.startsWith("SELECT payload_json,status")) {
                    return List.of(Map.of("payload_json", savedPayload[0], "status", "PREVIEW"));
                }
                return List.of();
            }

            @Override
            public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
                return List.of();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                return (T) "current-member-id";
            }

            @Override
            public int update(String sql, Object... args) {
                if (sql.startsWith("INSERT INTO ledger_import_batch")) {
                    savedPayload[0] = String.valueOf(args[4]);
                }
                return 1;
            }
        };
        LedgerBookAccess access = access(jdbc);
        ObjectMapper mapper = new ObjectMapper();
        LedgerTransactionService transactions = new LedgerTransactionService(jdbc, mapper, access, null, null) {
            @Override
            String matchAccount(LedgerBookAccess.Context context, String name) {
                return name + "-id";
            }

            @Override
            public Map<String, Object> create(String bookPublicId, Map<String, Object> input, String opId) {
                created.add(input);
                return input;
            }
        };
        LedgerImportService service = new LedgerImportService(jdbc, mapper, access, transactions);
        byte[] csv = ("交易类型,日期,转出账户,转入账户,金额\n"
                + "转账,2026-09-08,现金,银行卡,20\n").getBytes(StandardCharsets.UTF_8);
        Map<String, Object> preview = service.preview("test-book",
                new MockMultipartFile("file", "transfer.csv", null, csv), "AUTO");
        Map<String, Object> confirmed = service.confirm("test-book", String.valueOf(preview.get("batchId")));

        assertEquals(1L, confirmed.get("createdCount"));
        assertEquals(1, created.size());
        assertEquals("TRANSFER", created.get(0).get("kind"));
        assertEquals("现金-id", created.get(0).get("accountId"));
        assertEquals("银行卡-id", created.get(0).get("targetAccountId"));
        assertEquals("current-member-id", created.get(0).get("memberId"));
        assertTrue(!created.get(0).containsKey("categoryId"));
    }

    private LedgerImportService service() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                return List.of();
            }

            @Override
            public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
                return List.of();
            }

            @Override
            public int update(String sql, Object... args) {
                return 1;
            }
        };
        LedgerBookAccess access = access(jdbc);
        ObjectMapper mapper = new ObjectMapper();
        return new LedgerImportService(jdbc, mapper, access,
                new LedgerTransactionService(jdbc, mapper, access, null, null));
    }

    private LedgerBookAccess access(JdbcTemplate jdbc) {
        return new LedgerBookAccess(jdbc, null) {
            @Override
            public Context resolve(String publicId) {
                return new Context(1, publicId, 2, 2, 3, 4, "OWNER", Set.of("IMPORT_EXPORT"));
            }
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> preview) {
        return (List<Map<String, Object>>) preview.get("rows");
    }

    private void addSheet(XSSFWorkbook workbook, String name, String[] headers, String[] values) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        Row data = sheet.createRow(1);
        for (int column = 0; column < headers.length; column++) {
            header.createCell(column).setCellValue(headers[column]);
            data.createCell(column).setCellValue(values[column]);
        }
    }
}
