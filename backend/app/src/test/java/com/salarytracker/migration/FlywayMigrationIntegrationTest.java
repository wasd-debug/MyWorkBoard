package com.salarytracker.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlywayMigrationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("salary_migration")
            .withUsername("salary")
            .withPassword("salary");

    @Test
    @Order(1)
    void replaysAllMigrationsOnAnEmptyMySqlDatabase() throws Exception {
        Flyway flyway = flyway(false);

        flyway.migrate();
        flyway.validate();

        assertEquals("14", flyway.info().current().getVersion().getVersion());
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            assertTrue(tableExists(connection, "work_record"));
            assertEquals(39L, scalar(statement, "SELECT COUNT(*) FROM holiday WHERE year_key=2026"));
            assertTrue(tableExists(connection, "ledger_transaction"));
            assertTrue(tableExists(connection, "ledger_sync_oplog"));
            assertTrue(tableExists(connection, "ledger_scheduled_task"));
            assertTrue(indexExists(connection, "ledger_transaction", "idx_ledger_transaction_book_date"));
        }
    }

    @Test
    @Order(2)
    void upgradesLegacyWorktimeFixtureWithoutLosingUsersRecordsOrBooks() throws Exception {
        Flyway cleanup = flyway(false);
        cleanup.clean();
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE settings (id INT NOT NULL PRIMARY KEY, data TEXT NOT NULL)");
            statement.execute("CREATE TABLE records (`date` VARCHAR(10) NOT NULL PRIMARY KEY, `start` VARCHAR(5) NOT NULL, `end` VARCHAR(5) NOT NULL DEFAULT '', rest INT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE kv (`key` VARCHAR(64) NOT NULL PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO settings(id,data) VALUES (1,'{\"salaryPre\":18000,\"salaryPost\":14500,\"basis\":\"pre\",\"workStart\":\"08:30\",\"workEnd\":\"17:30\",\"lunchMin\":60,\"daysPerMonth\":21.75,\"autoDays\":false}')");
            statement.execute("INSERT INTO records(`date`,`start`,`end`,rest) VALUES ('2026-08-03','08:32','18:10',60),('2026-08-04','08:40','',45)");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .cleanDisabled(false)
                .load();
        flyway.migrate();
        flyway.validate();

        assertEquals("14", flyway.info().current().getVersion().getVersion());
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            assertEquals(1L, scalar(statement, "SELECT COUNT(*) FROM app_user"));
            assertEquals(2L, scalar(statement, "SELECT COUNT(*) FROM work_record WHERE user_id=1 AND deleted=FALSE"));
            assertEquals(1L, scalar(statement, "SELECT COUNT(*) FROM ledger_book WHERE owner_user_id=1 AND deleted=FALSE"));
            assertEquals(18000L, scalar(statement, "SELECT salary_pre FROM work_setting WHERE user_id=1"));
            assertEquals(2L, scalar(statement, "SELECT source_count FROM migration_reconciliation WHERE migration_name='legacy-records-to-work-record'"));
            assertEquals(2L, scalar(statement, "SELECT target_count FROM migration_reconciliation WHERE migration_name='legacy-records-to-work-record'"));
            assertEquals(-22L, scalar(statement, "SELECT overtime_min FROM work_record WHERE date='2026-08-03'"));
            assertEquals(2L, scalar(statement, "SELECT COUNT(*) FROM work_record WHERE calc_version='phase0-v2-day-type'"));
        }
    }

    private Flyway flyway(boolean baselineLegacy) {
        var configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (baselineLegacy) {
            configuration.baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("0"));
        }
        return configuration.load();
    }

    private long scalar(java.sql.Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet result = connection.getMetaData().getTables(
                connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean indexExists(Connection connection, String table, String index) throws Exception {
        try (ResultSet result = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, table, false, false)) {
            while (result.next()) {
                if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) return true;
            }
            return false;
        }
    }
}
